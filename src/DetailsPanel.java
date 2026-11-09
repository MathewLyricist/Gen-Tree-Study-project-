import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import javax.imageio.ImageIO;

class DetailsPanel extends JPanel implements ChangeListener {
    private EvolutionEngine engine;
    private JLabel nameLabel;
    private JLabel infoLabel;
    private JPanel traitsPanel;
    private JLabel imageLabel;
    private BufferedImage maleImage;
    private BufferedImage femaleImage;
    private JTextArea historyArea;
    private Human lastDisplayedHuman = null;
    private JButton showFamilyTreeButton;

    public DetailsPanel(EvolutionEngine engine) {
        this.engine = engine;
        engine.addChangeListener(this);

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Детали особи"));
        setPreferredSize(new Dimension(400, 800));

        initializeComponents();
        loadImages();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        SwingUtilities.invokeLater(this::updateDisplay);
    }

    private void initializeComponents() {
        JPanel topPanel = new JPanel(new BorderLayout());

        nameLabel = new JLabel("Не выбрано", SwingConstants.CENTER);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 18));
        nameLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        infoLabel = new JLabel(" ", SwingConstants.CENTER);
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        topPanel.add(nameLabel, BorderLayout.NORTH);
        topPanel.add(infoLabel, BorderLayout.SOUTH);

        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));

        JPanel imagePanel = new JPanel(new BorderLayout());
        imagePanel.add(imageLabel, BorderLayout.CENTER);
        imagePanel.setPreferredSize(new Dimension(380, 250));

        traitsPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        traitsPanel.setBorder(BorderFactory.createTitledBorder("Генетические признаки"));

        JScrollPane traitsScroll = new JScrollPane(traitsPanel);
        traitsScroll.setPreferredSize(new Dimension(380, 200));

        historyArea = new JTextArea(8, 30);
        historyArea.setEditable(false);
        historyArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);
        JScrollPane historyScroll = new JScrollPane(historyArea);
        historyScroll.setBorder(BorderFactory.createTitledBorder("Генеалогия"));

        showFamilyTreeButton = new JButton("Показать семейное дерево");
        showFamilyTreeButton.setEnabled(false);
        showFamilyTreeButton.addActionListener(e -> {
            Human human = engine.getSelectedHuman();
            if (human != null) {
                JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
                FamilyTreeDialog dialog = new FamilyTreeDialog(parentFrame, engine, human);
                dialog.setVisible(true);
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(showFamilyTreeButton);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(imagePanel, BorderLayout.NORTH);
        centerPanel.add(traitsScroll, BorderLayout.CENTER);
        centerPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(historyScroll, BorderLayout.SOUTH);
    }

    private void loadImages() {
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    File maleFile = new File("man.jpeg");
                    if (!maleFile.exists()) {
                        maleFile = new File("src/man.jpeg");
                    }
                    if (!maleFile.exists()) {
                        maleFile = new File("./man.jpeg");
                    }
                    if (maleFile.exists()) {
                        maleImage = ImageIO.read(maleFile);
                    } else {
                        createDefaultMaleImage();
                    }

                    File femaleFile = new File("woman.jpeg");
                    if (!femaleFile.exists()) {
                        femaleFile = new File("src/woman.jpeg");
                    }
                    if (!femaleFile.exists()) {
                        femaleFile = new File("./woman.jpeg");
                    }
                    if (femaleFile.exists()) {
                        femaleImage = ImageIO.read(femaleFile);
                    } else {
                        createDefaultFemaleImage();
                    }
                } catch (Exception e) {
                    System.err.println("Ошибка загрузки изображений: " + e.getMessage());
                    createDefaultImages();
                }
                return null;
            }

            @Override
            protected void done() {
                updateDisplay();
            }
        };

        worker.execute();
    }

    private void createDefaultMaleImage() {
        maleImage = new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = maleImage.createGraphics();

        GradientPaint gradient = new GradientPaint(0, 0, new Color(173, 216, 230),
                300, 400, new Color(135, 206, 235));
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, 300, 400);

        g2d.setColor(new Color(100, 150, 200));
        g2d.fillOval(100, 50, 100, 100);
        g2d.fillRect(125, 150, 50, 150);
        g2d.fillRect(75, 150, 50, 20);
        g2d.fillRect(175, 150, 50, 20);
        g2d.fillRect(125, 300, 20, 80);
        g2d.fillRect(155, 300, 20, 80);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        g2d.drawString("♂ Мужчина", 90, 200);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString("(файл man.jpeg)", 95, 220);
        g2d.drawString("не найден", 110, 240);

        g2d.dispose();
    }

    private void createDefaultFemaleImage() {
        femaleImage = new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = femaleImage.createGraphics();

        GradientPaint gradient = new GradientPaint(0, 0, new Color(255, 182, 193),
                300, 400, new Color(255, 105, 180));
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, 300, 400);

        g2d.setColor(new Color(200, 120, 160));
        g2d.fillOval(100, 50, 100, 100);
        g2d.fillRect(125, 150, 50, 100);
        int[] xPoints = {100, 200, 175, 125, 100};
        int[] yPoints = {250, 250, 300, 300, 250};
        g2d.fillPolygon(xPoints, yPoints, 5);
        g2d.fillRect(75, 150, 50, 20);
        g2d.fillRect(175, 150, 50, 20);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        g2d.drawString("♀ Женщина", 90, 200);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString("(файл woman.jpeg)", 90, 220);
        g2d.drawString("не найден", 110, 240);

        g2d.dispose();
    }

    private void createDefaultImages() {
        createDefaultMaleImage();
        createDefaultFemaleImage();
    }

    private void updateDisplay() {
        Human human = engine.getSelectedHuman();

        if (human == lastDisplayedHuman && human != null) {
            return;
        }

        lastDisplayedHuman = human;

        if (human == null) {
            nameLabel.setText("Не выбрано");
            infoLabel.setText("Выберите особь на дереве");
            traitsPanel.removeAll();
            historyArea.setText("");
            showFamilyTreeButton.setEnabled(false);

            if (maleImage != null) {
                Image scaledImage = maleImage.getScaledInstance(350, 230, Image.SCALE_SMOOTH);
                imageLabel.setIcon(new ImageIcon(scaledImage));
            }
            return;
        }

        nameLabel.setText(human.name);

        double height = human.getHeight();
        double weight = human.getWeight();
        double heightM = height / 100.0;
        double bmi = weight / (heightM * heightM);

        infoLabel.setText(String.format("<html>%s | ID: %d | Поколение: %d | %s<br>Приспособленность: %.2f | Рост: %.0f см | Вес: %.1f кг | ИМТ: %.1f</html>",
                human.getGenderText(), human.id, human.generation, human.getStatus(),
                human.fitness, height, weight, bmi));

        SwingUtilities.invokeLater(() -> {
            BufferedImage currentImage = human.isMale ? maleImage : femaleImage;
            if (currentImage != null) {
                Image scaledImage = currentImage.getScaledInstance(350, 230, Image.SCALE_SMOOTH);
                imageLabel.setIcon(new ImageIcon(scaledImage));
            }
        });

        SwingUtilities.invokeLater(() -> {
            traitsPanel.removeAll();

            addTraitCategory("Физические параметры:",
                    new String[]{"Рост", "Вес", "Телосложение", "Мускулатура", "Размер ушей"});
            addTraitCategory("Внешность:",
                    new String[]{"Цвет глаз", "Цвет волос", "Цвет кожи", "Форма лица", "Разрез глаз"});
            addTraitCategory("Детали:",
                    new String[]{"Форма носа", "Форма губ", "Тип волос", "Лоб", "Подбородок"});
            addTraitCategory("Особенности:",
                    new String[]{"Наличие веснушек"});

            traitsPanel.revalidate();
            traitsPanel.repaint();

            updateHistory(human);

            showFamilyTreeButton.setEnabled(true);
            showFamilyTreeButton.setText("Показать семейное дерево");
        });
    }

    private void addTraitCategory(String categoryName, String[] traitNames) {
        Human human = engine.getSelectedHuman();
        if (human == null) return;

        traitsPanel.add(new JLabel(" "));
        JLabel categoryLabel = new JLabel(categoryName);
        categoryLabel.setFont(new Font("Arial", Font.BOLD, 12));
        categoryLabel.setForeground(new Color(0, 0, 150));
        traitsPanel.add(categoryLabel);

        for (String traitName : traitNames) {
            if (human.traits.containsKey(traitName)) {
                JLabel nameLabel = new JLabel("  " + traitName + ":");
                nameLabel.setFont(new Font("Arial", Font.PLAIN, 11));

                String value = human.getTraitDisplay(traitName);
                JLabel valueLabel = new JLabel(value);
                valueLabel.setFont(new Font("Arial", Font.BOLD, 11));

                if (traitName.equals("Рост") || traitName.equals("Вес")) {
                    valueLabel.setForeground(new Color(0, 100, 0));
                } else if (traitName.equals("Мускулатура")) {
                    if (value.equals("Сильная")) valueLabel.setForeground(new Color(0, 150, 0));
                    else if (value.equals("Средняя")) valueLabel.setForeground(new Color(200, 150, 0));
                    else valueLabel.setForeground(Color.BLACK);
                } else if (traitName.contains("Цвет")) {
                    valueLabel.setForeground(new Color(150, 0, 0));
                } else if (traitName.equals("Телосложение")) {
                    if (value.equals("Нормостеническое")) valueLabel.setForeground(new Color(0, 150, 0));
                    else valueLabel.setForeground(Color.BLACK);
                } else {
                    valueLabel.setForeground(Color.BLACK);
                }

                traitsPanel.add(nameLabel);
                traitsPanel.add(valueLabel);
            }
        }
    }

    private void updateHistory(Human human) {
        StringBuilder history = new StringBuilder();

        history.append("=== ГЕНЕАЛОГИЧЕСКОЕ ДРЕВО ===\n\n");

        history.append("ПОКОЛЕНИЕ: ").append(human.generation).append("\n");
        history.append("ВОЗРАСТ: ").append(human.age).append(" лет\n");
        history.append("СТАТУС: ").append(human.alive ? "ЖИВ" : "УМЕР").append("\n");

        history.append("\n=== РОДИТЕЛИ ===\n");

        history.append("ОТЕЦ: ");
        if (human.father != null) {
            history.append(human.father.name)
                    .append(" (ID: ").append(human.father.id)
                    .append(", Gen ").append(human.father.generation)
                    .append(", ").append(human.father.age).append(" лет")
                    .append(", ").append(human.father.alive ? "жив" : "умер")
                    .append(")\n");
        } else {
            history.append("Неизвестен (основатель)\n");
        }

        history.append("МАТЬ: ");
        if (human.mother != null) {
            history.append(human.mother.name)
                    .append(" (ID: ").append(human.mother.id)
                    .append(", Gen ").append(human.mother.generation)
                    .append(", ").append(human.mother.age).append(" лет")
                    .append(", ").append(human.mother.alive ? "жива" : "умерла")
                    .append(", супруг: ").append(getSpouseInfo(human.mother))
                    .append(")\n");
        } else {
            history.append("Неизвестна (основатель)\n");
        }

        history.append("\n=== ДЕТИ (").append(human.children.size()).append(") ===\n");
        if (human.children.isEmpty()) {
            history.append("  Нет детей\n");
        } else {
            Map<Integer, List<Human>> childrenByGen = new TreeMap<>();
            for (Human child : human.children) {
                childrenByGen.computeIfAbsent(child.generation, k -> new ArrayList<>()).add(child);
            }

            for (Map.Entry<Integer, List<Human>> entry : childrenByGen.entrySet()) {
                history.append("  Поколение ").append(entry.getKey()).append(":\n");
                for (Human child : entry.getValue()) {
                    String fatherInfo = child.father != null ?
                            child.father.name + " (ID:" + child.father.id + ")" : "неизвестен";
                    String motherInfo = child.mother != null ?
                            child.mother.name + " (ID:" + child.mother.id + ")" : "неизвестна";

                    history.append("    • ").append(child.name)
                            .append(" (ID:").append(child.id)
                            .append(", ").append(child.getGenderSymbol())
                            .append(", ").append(child.age).append(" лет")
                            .append(", ").append(child.alive ? "жив" : "умер")
                            .append(")\n")
                            .append("        Отец: ").append(fatherInfo)
                            .append(", Мать: ").append(motherInfo).append("\n");
                }
            }
        }

        history.append("\n=== БРАТЬЯ/СЕСТРЫ ===\n");
        if (human.father != null && human.mother != null) {
            List<Human> siblings = new ArrayList<>();
            for (Human child : human.father.children) {
                if (child != human && child.mother == human.mother && !siblings.contains(child)) {
                    siblings.add(child);
                }
            }

            if (siblings.isEmpty()) {
                history.append("  Нет братьев/сестер\n");
            } else {
                for (Human sibling : siblings) {
                    history.append("  • ").append(sibling.name)
                            .append(" (ID:").append(sibling.id)
                            .append(", ").append(sibling.getGenderSymbol())
                            .append(", Gen ").append(sibling.generation)
                            .append(")\n");
                }
            }
        } else {
            history.append("  Нет информации (основатель)\n");
        }

        historyArea.setText(history.toString());
    }

    private String getSpouseInfo(Human person) {
        if (person == null) return "нет";

        for (Human child : person.children) {
            if (person.isMale && child.mother != null) {
                return child.mother.name + " (ID:" + child.mother.id + ")";
            } else if (!person.isMale && child.father != null) {
                return child.father.name + " (ID:" + child.father.id + ")";
            }
        }
        return "нет";
    }
}