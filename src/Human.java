import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


class Human {

    private static final ThreadLocal<Random> RANDOM = ThreadLocal.withInitial(Random::new);

    final int id;
    final String name;
    final boolean isMale;
    final int generation;
    Human father;
    Human mother;
    final List<Human> children;
    final Map<String, Object> traits;
    int age = 0;
    volatile boolean alive = true;
    int deathAge = -1;
    double fitness = 0;

    public static final Map<String, String[]> TRAIT_VALUES;
    public static final String[] TRAIT_NAMES;

    static {
        Map<String, String[]> map = new HashMap<>();
        map.put("Цвет глаз", new String[]{"Карие", "Голубые", "Зеленые", "Серые", "Черные"});
        map.put("Цвет волос", new String[]{"Черные", "Каштановые", "Русые", "Рыжие", "Светлые"});
        map.put("Цвет кожи", new String[]{"Светлая", "Средняя", "Смуглая", "Оливковая"});
        map.put("Телосложение", new String[]{"Астеническое", "Нормостеническое", "Гиперстеническое"});
        map.put("Тип волос", new String[]{"Прямые", "Волнистые", "Курчавые"});
        map.put("Форма лица", new String[]{"Овальная", "Круглая", "Квадратная", "Сердцевидная"});
        map.put("Разрез глаз", new String[]{"Миндалевидный", "Круглый", "Раскосый"});
        map.put("Форма носа", new String[]{"Прямой", "Курносый", "Орлиный", "Широкий"});
        map.put("Форма губ", new String[]{"Тонкие", "Средние", "Пухлые"});
        map.put("Лоб", new String[]{"Высокий", "Средний", "Низкий"});
        map.put("Подбородок", new String[]{"Острый", "Квадратный", "Круглый"});
        map.put("Наличие веснушек", new String[]{"Да", "Нет"});
        TRAIT_VALUES = Collections.unmodifiableMap(map);

        List<String> allTraits = new ArrayList<>(TRAIT_VALUES.keySet());
        allTraits.addAll(Arrays.asList("Рост", "Вес", "Мускулатура", "Размер ушей"));
        TRAIT_NAMES = allTraits.toArray(new String[0]);
    }

    public Human(String name, boolean isMale, int generation,
                 Human father, Human mother, Random rand, int id) {
        this.id = id;
        this.name = name;
        this.isMale = isMale;
        this.generation = generation;
        this.father = father;
        this.mother = mother;
        this.children = Collections.synchronizedList(new ArrayList<>());
        this.traits = new ConcurrentHashMap<>();

        if (father == null || mother == null) {
            initializeRandomTraits(rand);
        } else {
            inheritTraits(father, mother, rand);
        }

        mutateTraits(rand);
        calculateFitness();

        if (!traits.containsKey("Телосложение")) {
            calculateBodyType();
        }

        if (father != null) father.children.add(this);
        if (mother != null) mother.children.add(this);
    }

    void calculateFitness() {
        double height = (Double) traits.get("Рост");
        double weight = (Double) traits.get("Вес");
        String muscleStr = (String) traits.get("Мускулатура");

        double musculature = switch (muscleStr) {
            case "Сильная" -> 0.9;
            case "Средняя" -> 0.6;
            default -> 0.3;
        };

        double heightM = height / 100.0;
        double bmi = weight / (heightM * heightM);
        double bmiFitness = 1.0 - Math.abs(bmi - 22) / 10.0;

        double ageFactor = 1.0 - Math.max(0, (age - 30) / 70.0);

        fitness = (bmiFitness * 0.4 + musculature * 0.3 + ageFactor * 0.3);
        fitness = Math.clamp(fitness, 0, 1);
    }

    public void ageOneYear(Random rand) {
        age++;

        if (!alive) return;

        double baseLongevity = 70 + (fitness * 25);

        double deathChance;

        if (age < 18) {
            deathChance = 0.002;
        } else if (age < 30) {
            deathChance = 0.005;
        } else if (age < baseLongevity) {
            double ageFactor = (age - 30.0) / (baseLongevity - 30.0);
            deathChance = 0.01 + ageFactor * 0.08;
        } else if (age < 100) {
            double ageFactor = (age - baseLongevity) / (100.0 - baseLongevity);
            deathChance = 0.1 + ageFactor * 0.8;
        } else {
            deathChance = 1.0;
        }

        deathChance *= (1.2 - fitness * 0.4);

        if (age >= 110) {
            alive = false;
            deathAge = age;
            return;
        }

        if (rand.nextDouble() < deathChance) {
            alive = false;
            deathAge = age;
        }
    }

    public boolean canReproduce() {
        return alive && age >= 16 && age <= 50 && fitness > 0.3;
    }

    public String getGenderSymbol() {
        return isMale ? "♂" : "♀";
    }

    public String getGenderText() {
        return isMale ? "Мужчина" : "Женщина";
    }

    public String getStatus() {
        if (alive) {
            return String.format("Жив, %d лет", age);
        } else {
            return String.format("Умер в %d лет", deathAge);
        }
    }

    public String getTraitDisplay(String traitName) {
        Object value = traits.get(traitName);

        if (traitName.equals("Рост")) {
            return String.format("%.0f см", (Double) value);
        } else if (traitName.equals("Вес")) {
            return String.format("%.1f кг", (Double) value);
        }

        return value.toString();
    }

    @Override
    public String toString() {
        return String.format("%s (ID %d, Gen %d, %s)",
                name, id, generation,
                alive ? age + " лет" : "умер");
    }

    public double getHeight() { return (Double) traits.get("Рост"); }
    public double getWeight() { return (Double) traits.get("Вес"); }
    public String getMuscle() { return (String) traits.get("Мускулатура"); }
    public String getEarSize() { return (String) traits.get("Размер ушей"); }

    public boolean isFounder() {
        return father == null && mother == null && generation == 0;
    }

    private void initializeRandomTraits(Random rand) {
        double baseHeight = isMale ? 165 : 155;
        double height = baseHeight + rand.nextDouble() * 25;
        traits.put("Рост", height);

        double idealWeight = calculateIdealWeight(height, isMale);
        double weight = idealWeight * (0.85 + rand.nextDouble() * 0.3);
        traits.put("Вес", weight);

        String[] muscleOptions = {"Слабая", "Средняя", "Сильная"};
        traits.put("Мускулатура", muscleOptions[rand.nextInt(muscleOptions.length)]);

        String[] earOptions = {"Маленькие", "Средние", "Большие"};
        traits.put("Размер ушей", earOptions[rand.nextInt(earOptions.length)]);

        for (Map.Entry<String, String[]> entry : TRAIT_VALUES.entrySet()) {
            String[] values = entry.getValue();
            traits.put(entry.getKey(), values[rand.nextInt(values.length)]);
        }
    }

    private double calculateIdealWeight(double height, boolean isMale) {
        return (height - 100) * (isMale ? 1.1 : 1.05);
    }

    private void inheritTraits(Human father, Human mother, Random rand) {
        double fatherHeight = (Double) father.traits.get("Рост");
        double motherHeight = (Double) mother.traits.get("Рост");

        double baseHeight = (fatherHeight + motherHeight) / 2.0;
        baseHeight += isMale ? 5 : -5;

        double height = baseHeight * (0.95 + rand.nextDouble() * 0.1);
        traits.put("Рост", height);

        double idealWeight = calculateIdealWeight(height, isMale);
        double fatherWeight = (Double) father.traits.get("Вес");
        double motherWeight = (Double) mother.traits.get("Вес");
        double weight = idealWeight * 0.7 + ((fatherWeight + motherWeight) / 2.0) * 0.3;
        weight *= (0.85 + rand.nextDouble() * 0.3);
        traits.put("Вес", weight);

        String fatherMuscle = (String) father.traits.get("Мускулатура");
        String motherMuscle = (String) mother.traits.get("Мускулатура");
        String[] muscleOptions = {"Слабая", "Средняя", "Сильная"};

        String inheritedMuscle = rand.nextBoolean() ? fatherMuscle : motherMuscle;
        if (rand.nextDouble() < 0.2) {
            int currentIdx = indexOf(muscleOptions, inheritedMuscle);
            if (currentIdx >= 0) {
                if (rand.nextBoolean() && currentIdx < muscleOptions.length - 1) {
                    inheritedMuscle = muscleOptions[currentIdx + 1];
                } else if (currentIdx > 0) {
                    inheritedMuscle = muscleOptions[currentIdx - 1];
                }
            }
        }
        traits.put("Мускулатура", inheritedMuscle);

        String fatherEars = (String) father.traits.get("Размер ушей");
        String motherEars = (String) mother.traits.get("Размер ушей");
        String[] earOptions = {"Маленькие", "Средние", "Большие"};
        traits.put("Размер ушей", rand.nextBoolean() ? fatherEars : motherEars);

        for (String traitName : TRAIT_VALUES.keySet()) {
            if (traitName.equals("Телосложение")) continue;

            String fatherValue = (String) father.traits.get(traitName);
            String motherValue = (String) mother.traits.get(traitName);

            String inheritedValue = rand.nextBoolean() ? fatherValue : motherValue;

            if (isDominantTrait(traitName, fatherValue) && rand.nextDouble() < 0.7) {
                inheritedValue = fatherValue;
            } else if (isDominantTrait(traitName, motherValue) && rand.nextDouble() < 0.7) {
                inheritedValue = motherValue;
            }

            traits.put(traitName, inheritedValue);
        }
    }

    private int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return -1;
    }

    private boolean isDominantTrait(String traitName, String value) {
        return switch (traitName) {
            case "Цвет глаз" -> value.equals("Карие") || value.equals("Черные");
            case "Цвет волос" -> value.equals("Черные") || value.equals("Каштановые");
            case "Тип волос" -> value.equals("Курчавые");
            case "Наличие веснушек" -> value.equals("Нет");
            default -> false;
        };
    }

    private void mutateTraits(Random rand) {
        if (rand.nextDouble() < 0.05) {
            double currentHeight = (Double) traits.get("Рост");
            traits.put("Рост", Math.clamp(
                    currentHeight + (rand.nextDouble() - 0.5) * 10, 150, 200));
        }

        if (rand.nextDouble() < 0.05) {
            double currentWeight = (Double) traits.get("Вес");
            traits.put("Вес", Math.clamp(
                    currentWeight + (rand.nextDouble() - 0.5) * 10, 40, 150));
        }

        if (rand.nextDouble() < 0.05) {
            String[] muscleOptions = {"Слабая", "Средняя", "Сильная"};
            String current = (String) traits.get("Мускулатура");
            int idx = indexOf(muscleOptions, current);
            if (idx >= 0) {
                int newIdx;
                do {
                    newIdx = rand.nextInt(muscleOptions.length);
                } while (newIdx == idx);
                traits.put("Мускулатура", muscleOptions[newIdx]);
            }
        }

        for (String traitName : TRAIT_VALUES.keySet()) {
            if (rand.nextDouble() < 0.02) {
                String[] values = TRAIT_VALUES.get(traitName);
                traits.put(traitName, values[rand.nextInt(values.length)]);
            }
        }
    }

    private void calculateBodyType() {
        double height = (Double) traits.get("Рост");
        double weight = (Double) traits.get("Вес");

        double heightM = height / 100.0;
        double bmi = weight / (heightM * heightM);

        String bodyType;
        if (bmi < 18.5) {
            bodyType = "Астеническое";
        } else if (bmi < 25) {
            bodyType = "Нормостеническое";
        } else {
            bodyType = "Гиперстеническое";
        }

        traits.put("Телосложение", bodyType);
    }
}