import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.SwingUtilities;

class EvolutionEngine {
    private List<Human> allPeople = new CopyOnWriteArrayList<>();
    private List<Human> alivePeople = new CopyOnWriteArrayList<>();
    private int currentYear = 0;
    private Human selectedHuman = null;
    private ThreadLocal<Random> rand = ThreadLocal.withInitial(Random::new);
    private List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    private int maxPopulation = 100;
    private double mutationRate = 0.1;
    private int maxChildrenPerCouple = 3;
    private volatile boolean evolutionRunning = false;
    private int maleFoundersAdded = 0;
    private int femaleFoundersAdded = 0;

    private ExecutorService executorService;
    private int threadPoolSize = Runtime.getRuntime().availableProcessors();
    private AtomicInteger nextId = new AtomicInteger(1);
    private final Object updateLock = new Object();

    public EvolutionEngine() {
        executorService = Executors.newFixedThreadPool(threadPoolSize);
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }

    public void initializePopulation(int founderCount) {
        synchronized (updateLock) {
            allPeople.clear();
            alivePeople.clear();
            currentYear = 0;
            selectedHuman = null;
            maleFoundersAdded = 0;
            femaleFoundersAdded = 0;
            nextId.set(1);

            List<Callable<Human>> tasks = new ArrayList<>();

            for (int i = 0; i < founderCount; i++) {
                final boolean needMale = needsMaleFounder();
                tasks.add(() -> createRandomFounderWithGender(needMale));
            }

            try {
                List<Future<Human>> futures = executorService.invokeAll(tasks);
                for (Future<Human> future : futures) {
                    Human founder = future.get();
                    allPeople.add(founder);
                    alivePeople.add(founder);
                    if (founder.isMale) maleFoundersAdded++;
                    else femaleFoundersAdded++;
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        notifyListeners();
    }

    public void addRandomFounder() {
        boolean needMale = needsMaleFounder();
        Human founder = createRandomFounderWithGender(needMale);

        synchronized (updateLock) {
            allPeople.add(founder);
            alivePeople.add(founder);
            if (founder.isMale) maleFoundersAdded++;
            else femaleFoundersAdded++;
        }

        notifyListeners();
    }

    public void addCustomFounder(String name, boolean isMale, Map<String, Object> traits) {
        Human founder = new Human(name, isMale, 0, null, null, rand.get(), nextId.getAndIncrement());
        if (traits != null) {
            for (Map.Entry<String, Object> entry : traits.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    founder.traits.put(entry.getKey(), entry.getValue());
                }
            }
            founder.calculateFitness();
        }

        synchronized (updateLock) {
            allPeople.add(founder);
            alivePeople.add(founder);
            if (isMale) maleFoundersAdded++;
            else femaleFoundersAdded++;
        }

        notifyListeners();
    }

    public boolean needsMaleFounder() {
        return maleFoundersAdded <= femaleFoundersAdded;
    }

    public void evolveOneYear() {
        if (alivePeople.isEmpty()) return;

        currentYear++;

        List<Future<Void>> agingFutures = new ArrayList<>();
        List<Human> aliveCopy = new ArrayList<>(alivePeople);

        int batchSize = Math.max(1, aliveCopy.size() / threadPoolSize);

        for (int i = 0; i < aliveCopy.size(); i += batchSize) {
            final int start = i;
            final int end = Math.min(i + batchSize, aliveCopy.size());
            final List<Human> batch = aliveCopy.subList(start, end);

            agingFutures.add(executorService.submit(() -> {
                List<Human> deadInBatch = new ArrayList<>();
                Random localRand = rand.get();

                for (Human human : batch) {
                    human.ageOneYear(localRand);
                    if (!human.alive) {
                        deadInBatch.add(human);
                    }
                }

                if (!deadInBatch.isEmpty()) {
                    synchronized (updateLock) {
                        alivePeople.removeAll(deadInBatch);
                    }
                }
                return null;
            }));
        }

        waitForFutures(agingFutures);

        List<Human> males = new ArrayList<>();
        List<Human> females = new ArrayList<>();

        Random localRand = rand.get();
        for (Human human : alivePeople) {
            if (human.canReproduce()) {
                if (localRand.nextDouble() < 0.7) {
                    if (human.isMale) {
                        males.add(human);
                    } else {
                        females.add(human);
                    }
                }
            }
        }

        Collections.shuffle(males, localRand);
        Collections.shuffle(females, localRand);

        List<Human> newChildren = Collections.synchronizedList(new ArrayList<>());
        int pairs = Math.min(males.size(), females.size());

        List<Future<Void>> reproductionFutures = new ArrayList<>();
        int reproductionBatchSize = Math.max(1, pairs / threadPoolSize);

        for (int i = 0; i < pairs; i += reproductionBatchSize) {
            final int start = i;
            final int end = Math.min(i + reproductionBatchSize, pairs);

            reproductionFutures.add(executorService.submit(() -> {
                Random threadRand = rand.get();
                List<Human> childrenBatch = new ArrayList<>();

                for (int j = start; j < end; j++) {
                    Human male = males.get(j);
                    Human female = females.get(j);

                    int childGeneration = Math.max(male.generation, female.generation) + 1;

                    double maleFertility = 1.0 - Math.max(0, (male.age - 35) / 20.0);
                    double femaleFertility = 1.0 - Math.max(0, (female.age - 30) / 20.0);
                    double fertility = (maleFertility + femaleFertility) / 2.0;

                    int childrenCount = threadRand.nextInt(maxChildrenPerCouple + 1);

                    for (int k = 0; k < childrenCount; k++) {
                        if (threadRand.nextDouble() < fertility * 0.7) {
                            String[] maleNames = {"Александр", "Дмитрий", "Михаил", "Иван", "Сергей"};
                            String[] femaleNames = {"Анна", "Мария", "Екатерина", "Ольга", "Наталья"};

                            boolean childIsMale = threadRand.nextBoolean();
                            String childName = childIsMale ?
                                    maleNames[threadRand.nextInt(maleNames.length)] :
                                    femaleNames[threadRand.nextInt(femaleNames.length)];

                            Human child = new Human(childName, childIsMale,
                                    childGeneration, male, female, threadRand, nextId.getAndIncrement());
                            childrenBatch.add(child);
                        }
                    }
                }

                if (!childrenBatch.isEmpty()) {
                    synchronized (updateLock) {
                        newChildren.addAll(childrenBatch);
                    }
                }
                return null;
            }));
        }

        waitForFutures(reproductionFutures);

        if (!newChildren.isEmpty()) {
            synchronized (updateLock) {
                for (Human child : newChildren) {
                    allPeople.add(child);
                    alivePeople.add(child);
                    if (child.father != null) child.father.children.add(child);
                    if (child.mother != null) child.mother.children.add(child);
                }
            }
        }

        // 3. ЕСТЕСТВЕННЫЙ ОТБОР
        if (alivePeople.size() > maxPopulation) {
            List<Human> sortedAlive = new ArrayList<>(alivePeople);

            List<Human> sortedForRemoval = sortedAlive.parallelStream()
                    .filter(h -> h.age >= 18)
                    .sorted((h1, h2) -> {
                        double score1 = h1.age / 100.0 + (1 - h1.fitness);
                        double score2 = h2.age / 100.0 + (1 - h2.fitness);
                        return Double.compare(score2, score1);
                    })
                    .collect(Collectors.toList());

            int toRemove = Math.max(0, alivePeople.size() - maxPopulation);
            List<Human> toRemoveList = new ArrayList<>();

            int maxToRemove = Math.max(1, alivePeople.size() / 10);
            toRemove = Math.min(toRemove, maxToRemove);

            for (int i = 0; i < toRemove && i < sortedForRemoval.size(); i++) {
                toRemoveList.add(sortedForRemoval.get(i));
            }

            synchronized (updateLock) {
                for (Human removed : toRemoveList) {
                    alivePeople.remove(removed);
                    removed.alive = false;
                    removed.deathAge = removed.age;
                }
            }
        }

        SwingUtilities.invokeLater(this::notifyListeners);
    }

    public void evolveYears(int years) {
        evolutionRunning = true;

        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < years && evolutionRunning; i++) {
                long startTime = System.currentTimeMillis();

                evolveOneYear();

                try {
                    long elapsed = System.currentTimeMillis() - startTime;
                    long sleepTime = Math.max(50, 300 - elapsed);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    break;
                }
            }
            evolutionRunning = false;

            SwingUtilities.invokeLater(this::notifyListeners);
        }, executorService);
    }

    public void stopEvolution() {
        evolutionRunning = false;
    }

    public void selectHuman(Human human) {
        selectedHuman = human;
        SwingUtilities.invokeLater(this::notifyListeners);
    }

    public List<Human> getFamilyTree(Human founder, int maxDepth) {
        List<Human> family = new ArrayList<>();
        if (founder == null) return family;

        Set<Human> visited = new HashSet<>();
        getAllDescendants(founder, family, visited, 0, maxDepth);

        addSpouses(family, visited);

        return family;
    }

    public List<Human> getFamilyTree(Human founder) {
        return getFamilyTree(founder, Integer.MAX_VALUE);
    }

    public List<Human> getAllDescendants(Human founder) {
        return getFamilyTree(founder, Integer.MAX_VALUE);
    }

    public List<Human> getFounders() {
        synchronized (updateLock) {
            return allPeople.stream()
                    .filter(Human::isFounder)
                    .collect(Collectors.toList());
        }
    }

    public List<Human> getAllPeople() {
        synchronized (updateLock) {
            return new ArrayList<>(allPeople);
        }
    }

    public List<Human> getAlivePeople() {
        synchronized (updateLock) {
            return new ArrayList<>(alivePeople);
        }
    }

    public int getCurrentYear() { return currentYear; }

    public Human getSelectedHuman() { return selectedHuman; }

    public int getTotalPeopleCount() {
        synchronized (updateLock) {
            return allPeople.size();
        }
    }

    public int getAlivePeopleCount() {
        synchronized (updateLock) {
            return alivePeople.size();
        }
    }

    public boolean isEvolutionRunning() { return evolutionRunning; }

    public double getAverageFitness() {
        List<Human> aliveCopy;
        synchronized (updateLock) {
            aliveCopy = new ArrayList<>(alivePeople);
        }

        if (aliveCopy.isEmpty()) return 0;

        return aliveCopy.parallelStream()
                .mapToDouble(h -> h.fitness)
                .average()
                .orElse(0);
    }

    public double getBestFitness() {
        List<Human> aliveCopy;
        synchronized (updateLock) {
            aliveCopy = new ArrayList<>(alivePeople);
        }

        if (aliveCopy.isEmpty()) return 0;

        return aliveCopy.parallelStream()
                .mapToDouble(h -> h.fitness)
                .max()
                .orElse(0);
    }

    public int getMaxGeneration() {
        List<Human> allCopy;
        synchronized (updateLock) {
            allCopy = new ArrayList<>(allPeople);
        }

        return allCopy.parallelStream()
                .mapToInt(h -> h.generation)
                .max()
                .orElse(0);
    }

    public void setMaxPopulation(int max) { this.maxPopulation = max; }
    public void setMutationRate(double rate) { this.mutationRate = rate; }
    public void setChildrenPerCouple(int children) { this.maxChildrenPerCouple = children; }

    public void setThreadPoolSize(int size) {
        if (size > 0 && size <= 32) {
            this.threadPoolSize = size;
            if (executorService != null) {
                executorService.shutdown();
                executorService = Executors.newFixedThreadPool(size);
            }
        }
    }

    public void addChangeListener(ChangeListener listener) {
        listeners.add(listener);
    }

    private void getAllDescendants(Human person, List<Human> family,
                                   Set<Human> visited, int depth, int maxDepth) {
        if (person == null || visited.contains(person) || depth > maxDepth) return;

        family.add(person);
        visited.add(person);

        for (Human child : person.children) {
            if (!visited.contains(child)) {
                getAllDescendants(child, family, visited, depth + 1, maxDepth);
            }
        }
    }

    private void addSpouses(List<Human> family, Set<Human> visited) {
        Set<Human> spouses = new HashSet<>();

        for (Human person : family) {
            for (Human child : person.children) {
                Human spouse = null;
                if (child.father == person) {
                    spouse = child.mother;
                } else if (child.mother == person) {
                    spouse = child.father;
                }

                if (spouse != null && !visited.contains(spouse) && !family.contains(spouse)) {
                    spouses.add(spouse);
                }
            }
        }

        family.addAll(spouses);
    }

    private Human findSpouse(Human person) {
        if (person == null) return null;

        for (Human child : person.children) {
            if (person.isMale && child.mother != null) {
                return child.mother;
            } else if (!person.isMale && child.father != null) {
                return child.father;
            }
        }
        return null;
    }

    private void notifyListeners() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : listeners) {
            listener.stateChanged(event);
        }
    }

    private Human createRandomFounderWithGender(boolean needMale) {
        String[] maleNames = {"Адам", "Ной", "Авраам", "Моисей", "Давид",
                "Иван", "Петр", "Алексей", "Сергей", "Дмитрий"};
        String[] femaleNames = {"Ева", "Сарра", "Ревекка", "Рахиль", "Лия",
                "Анна", "Мария", "Елена", "Ольга", "Наталья"};
        String[] surnames = {"Иванов", "Петров", "Сидоров", "Смирнов", "Кузнецов"};

        Random localRand = rand.get();
        boolean isMale = needMale;
        String name;
        if (isMale) {
            name = maleNames[localRand.nextInt(maleNames.length)] + " " +
                    surnames[localRand.nextInt(surnames.length)];
        } else {
            name = femaleNames[localRand.nextInt(femaleNames.length)] + " " +
                    surnames[localRand.nextInt(surnames.length)] + "а";
        }

        return new Human(name, isMale, 0, null, null, localRand, nextId.getAndIncrement());
    }

    private void waitForFutures(List<Future<Void>> futures) {
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }
}