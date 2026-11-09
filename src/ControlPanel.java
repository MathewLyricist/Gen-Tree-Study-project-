import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

class ControlPanel extends JPanel {
    private EvolutionEngine engine;
    private JTextField yearsField;
    private JTextField populationField;
    private JTextField foundersField;
    private JButton startButton;
    private JButton stopButton;
    private JButton stepButton;
    private JButton resetButton;
    private JButton addFounderButton;
    private JButton addCustomFounderButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JComboBox<String> founderTypeCombo;
    private javax.swing.Timer statusTimer;
    private JCheckBox optimizeRenderCheck;

    public ControlPanel(EvolutionEngine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Управление эволюцией"));

        initializeComponents();

        statusTimer = new javax.swing.Timer(100, e -> updateStatus());
        statusTimer.start();
    }

    public boolean isOptimizeRenderEnabled() {
        return optimizeRenderCheck.isSelected();
    }

    public JCheckBox getOptimizeRenderCheck() {
        return optimizeRenderCheck;
    }

    private void initializeComponents() {
        JPanel topPanel = new JPanel(new GridLayout(3, 4, 10, 5));

        topPanel.add(new JLabel("Лет эволюции:"));
        yearsField = new JTextField("50");
        topPanel.add(yearsField);

        topPanel.add(new JLabel("Макс. популяция:"));
        populationField = new JTextField("100");
        topPanel.add(populationField);

        topPanel.add(new JLabel("Основателей:"));
        foundersField = new JTextField("4");
        topPanel.add(foundersField);

        topPanel.add(new JLabel("Макс. детей на пару:"));
        JTextField childrenField = new JTextField("3");
        childrenField.addActionListener(e -> {
            try {
                engine.setChildrenPerCouple(Integer.parseInt(childrenField.getText()));
            } catch (NumberFormatException ex) {
                throw new RuntimeException(ex);
            }
        });
        topPanel.add(childrenField);

        topPanel.add(new JLabel("Задержка (мс):"));
        JTextField delayField = new JTextField("300");
        delayField.addActionListener(e -> {
            try {
                JOptionPane.showMessageDialog(this,
                        "Изменение задержки потребует перезапуска симуляции",
                        "Информация", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                throw new RuntimeException(ex);
            }
        });
        topPanel.add(delayField);

        optimizeRenderCheck = new JCheckBox("Оптимизировать рендеринг", true);
        topPanel.add(optimizeRenderCheck);
        topPanel.add(new JLabel(""));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));

        startButton = new JButton("▶ Запустить эволюцию");
        stopButton = new JButton("⏹ Стоп");
        stepButton = new JButton("⏭ Один год");
        resetButton = new JButton("🔄 Сброс");
        JButton perfButton = new JButton("⚙ Производительность");

        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(stepButton);
        buttonPanel.add(resetButton);
        buttonPanel.add(perfButton);

        JPanel founderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        founderPanel.setBorder(BorderFactory.createTitledBorder("Добавить основателей"));

        founderTypeCombo = new JComboBox<>(new String[]{
                "Случайный основатель (чередование)",
                "Мужчина (сильный тип)",
                "Женщина (средний тип)",
                "Мужчина (высокий рост)",
                "Женщина (светлые волосы)"
        });

        addFounderButton = new JButton("Добавить выбранного");
        addCustomFounderButton = new JButton("Добавить кастомного...");

        founderPanel.add(new JLabel("Тип:"));
        founderPanel.add(founderTypeCombo);
        founderPanel.add(addFounderButton);
        founderPanel.add(addCustomFounderButton);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Готово к работе");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);

        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);

        startButton.addActionListener(e -> startEvolution());
        stopButton.addActionListener(e -> {
            engine.stopEvolution();
            statusTimer.stop();
            statusTimer.start();
        });
        stepButton.addActionListener(e -> engine.evolveOneYear());
        resetButton.addActionListener(e -> resetSimulation());
        perfButton.addActionListener(e -> showPerformanceDialog());

        addFounderButton.addActionListener(e -> addFounder());
        addCustomFounderButton.addActionListener(e -> addCustomFounder());

        populationField.addActionListener(e -> {
            try {
                engine.setMaxPopulation(Integer.parseInt(populationField.getText()));
            } catch (NumberFormatException ex) {}
        });

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.CENTER);
        mainPanel.add(founderPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.NORTH);
        add(statusPanel, BorderLayout.SOUTH);
    }

    private void startEvolution() {
        try {
            int years = Integer.parseInt(yearsField.getText());
            int founders = Integer.parseInt(foundersField.getText());
            int maxPop = Integer.parseInt(populationField.getText());

            if (engine.getAlivePeopleCount() == 0) {
                if (founders % 2 != 0) founders++;
                engine.initializePopulation(founders);
            }

            engine.setMaxPopulation(maxPop);
            engine.evolveYears(years);

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "Проверьте правильность введенных чисел!",
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resetSimulation() {
        int result = JOptionPane.showConfirmDialog(this,
                "Вы уверены, что хотите сбросить симуляцию?\nВсе данные будут потеряны.",
                "Сброс симуляции", JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            engine.stopEvolution();
            engine.initializePopulation(0);
            statusTimer.restart();
        }
    }

    private void showPerformanceDialog() {
        JDialog dialog = new JDialog((Frame)null, "Настройки производительности", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(400, 250);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        int cores = Runtime.getRuntime().availableProcessors();
        panel.add(new JLabel("Количество потоков:"));
        JSlider threadSlider = new JSlider(1, cores * 2, Math.min(cores, 4));
        threadSlider.setMajorTickSpacing(2);
        threadSlider.setMinorTickSpacing(1);
        threadSlider.setPaintTicks(true);
        threadSlider.setPaintLabels(true);
        panel.add(threadSlider);

        JLabel threadLabel = new JLabel(threadSlider.getValue() + " потоков");
        panel.add(new JLabel("Рекомендуется: " + cores));
        panel.add(threadLabel);

        threadSlider.addChangeListener(e -> {
            threadLabel.setText(threadSlider.getValue() + " потоков");
        });

        panel.add(new JLabel("Размер батча:"));
        JComboBox<String> batchCombo = new JComboBox<>(new String[]{
                "Авто", "Мелкий (10)", "Средний (50)", "Крупный (100)"
        });
        batchCombo.setSelectedIndex(0);
        panel.add(batchCombo);

        JButton applyButton = new JButton("Применить");
        applyButton.addActionListener(e -> {
            try {
                int threads = threadSlider.getValue();
                engine.setThreadPoolSize(threads);

                String batchSize = (String) batchCombo.getSelectedItem();
                JOptionPane.showMessageDialog(dialog,
                        "Настройки применены!\n" +
                                "Потоки: " + threads + "\n" +
                                "Батчинг: " + batchSize,
                        "Успех", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog,
                        "Ошибка применения настроек: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton cancelButton = new JButton("Отмена");
        cancelButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(applyButton);
        buttonPanel.add(cancelButton);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void addFounder() {
        String type = (String) founderTypeCombo.getSelectedItem();
        Random rand = new Random();

        String[] maleNames = {"Адам", "Ной", "Авраам", "Моисей", "Давид"};
        String[] femaleNames = {"Ева", "Сарра", "Ревекка", "Рахиль", "Лия"};
        String[] surnames = {"Первый", "Второй", "Третий", "Четвертый", "Пятый"};

        boolean isMale = false;
        String name;

        switch (type) {
            case "Случайный основатель (чередование)":
                boolean needMale = engine.needsMaleFounder();
                isMale = needMale;
                if (needMale) {
                    name = maleNames[rand.nextInt(maleNames.length)] + " " +
                            surnames[rand.nextInt(surnames.length)];
                } else {
                    name = femaleNames[rand.nextInt(femaleNames.length)] + " " +
                            surnames[rand.nextInt(surnames.length)] + "а";
                }
                break;

            case "Мужчина (сильный тип)":
                isMale = true;
                name = maleNames[rand.nextInt(maleNames.length)] + " Сильный";
                break;

            case "Женщина (средний тип)":
                isMale = false;
                name = femaleNames[rand.nextInt(femaleNames.length)] + " Средняя";
                break;

            case "Мужчина (высокий рост)":
                isMale = true;
                name = maleNames[rand.nextInt(maleNames.length)] + " Высокий";
                break;

            case "Женщина (светлые волосы)":
                isMale = false;
                name = femaleNames[rand.nextInt(femaleNames.length)] + " Светлая";
                break;

            default:
                boolean defaultMale = engine.needsMaleFounder();
                isMale = defaultMale;
                if (defaultMale) {
                    name = maleNames[rand.nextInt(maleNames.length)] + " " +
                            surnames[rand.nextInt(surnames.length)];
                } else {
                    name = femaleNames[rand.nextInt(femaleNames.length)] + " " +
                            surnames[rand.nextInt(surnames.length)] + "а";
                }
        }

        Map<String, Object> traits = new HashMap<>();

        switch (type) {
            case "Мужчина (сильный тип)":
                traits.put("Мускулатура", "Сильная");
                traits.put("Рост", 185.0);
                traits.put("Вес", 85.0);
                break;

            case "Женщина (средний тип)":
                traits.put("Рост", 165.0);
                traits.put("Вес", 60.0);
                traits.put("Цвет волос", "Русые");
                traits.put("Цвет глаз", "Карие");
                break;

            case "Мужчина (высокий рост)":
                traits.put("Рост", 195.0);
                traits.put("Вес", 90.0);
                traits.put("Мускулатура", "Средняя");
                break;

            case "Женщина (светлые волосы)":
                traits.put("Цвет волос", "Светлые");
                traits.put("Цвет глаз", "Голубые");
                traits.put("Рост", 170.0);
                traits.put("Вес", 58.0);
                break;

            case "Случайный основатель (чередование)":
                if (isMale) {
                    traits.put("Рост", 170.0 + rand.nextDouble() * 20);
                    traits.put("Вес", 70.0 + rand.nextDouble() * 20);
                } else {
                    traits.put("Рост", 160.0 + rand.nextDouble() * 15);
                    traits.put("Вес", 55.0 + rand.nextDouble() * 15);
                }
                String[] muscles = {"Слабая", "Средняя", "Сильная"};
                traits.put("Мускулатура", muscles[rand.nextInt(muscles.length)]);
                break;
        }

        engine.addCustomFounder(name, isMale, traits);
    }

    private void addCustomFounder() {
        JDialog dialog = new JDialog((Frame)null, "Добавить кастомного основателя", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(500, 650);
        dialog.setLocationRelativeTo(this);

        JTabbedPane tabbedPane = new JTabbedPane();

        JPanel infoPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        JTextField nameField = new JTextField("Основатель");
        JComboBox<String> genderCombo = new JComboBox<>(new String[]{"Мужчина", "Женщина"});

        infoPanel.add(new JLabel("Имя:"));
        infoPanel.add(nameField);
        infoPanel.add(new JLabel("Пол:"));
        infoPanel.add(genderCombo);
        infoPanel.add(new JLabel(""));
        infoPanel.add(new JLabel(""));

        JPanel physicalPanel = new JPanel(new GridLayout(0, 4, 5, 5));

        physicalPanel.add(new JLabel("Рост (см):"));
        JSlider heightSlider = new JSlider(150, 200, 175);
        heightSlider.setMajorTickSpacing(10);
        heightSlider.setMinorTickSpacing(5);
        heightSlider.setPaintTicks(true);
        heightSlider.setPaintLabels(true);
        JLabel heightValue = new JLabel("175 см");
        heightSlider.addChangeListener(e -> {
            heightValue.setText(heightSlider.getValue() + " см");
        });
        physicalPanel.add(heightSlider);
        physicalPanel.add(new JLabel(""));
        physicalPanel.add(heightValue);

        physicalPanel.add(new JLabel("Вес (кг):"));
        JSlider weightSlider = new JSlider(40, 120, 70);
        weightSlider.setMajorTickSpacing(20);
        weightSlider.setMinorTickSpacing(5);
        weightSlider.setPaintTicks(true);
        weightSlider.setPaintLabels(true);
        JLabel weightValue = new JLabel("70 кг");
        weightSlider.addChangeListener(e -> {
            weightValue.setText(weightSlider.getValue() + " кг");
        });
        physicalPanel.add(weightSlider);
        physicalPanel.add(new JLabel(""));
        physicalPanel.add(weightValue);

        physicalPanel.add(new JLabel("Мускулатура:"));
        JComboBox<String> muscleCombo = new JComboBox<>(new String[]{"Слабая", "Средняя", "Сильная"});
        physicalPanel.add(muscleCombo);
        physicalPanel.add(new JLabel(""));
        physicalPanel.add(new JLabel(""));

        physicalPanel.add(new JLabel("Размер ушей:"));
        JComboBox<String> earCombo = new JComboBox<>(new String[]{"Маленькие", "Средние", "Большие"});
        physicalPanel.add(earCombo);
        physicalPanel.add(new JLabel(""));
        physicalPanel.add(new JLabel(""));

        JLabel bmiLabel = new JLabel("ИМТ: - ( - )");
        bmiLabel.setFont(new Font("Arial", Font.BOLD, 12));
        physicalPanel.add(new JLabel("Индекс массы тела:"));
        physicalPanel.add(bmiLabel);
        physicalPanel.add(new JLabel(""));
        physicalPanel.add(new JLabel(""));

        ChangeListener bmiUpdater = e -> {
            double height = heightSlider.getValue() / 100.0;
            double weight = weightSlider.getValue();
            double bmi = weight / (height * height);
            String bmiCategory;
            if (bmi < 18.5) bmiCategory = "Недостаточный";
            else if (bmi < 25) bmiCategory = "Нормальный";
            else if (bmi < 30) bmiCategory = "Избыточный";
            else bmiCategory = "Ожирение";
            bmiLabel.setText(String.format("ИМТ: %.1f (%s)", bmi, bmiCategory));
        };
        heightSlider.addChangeListener(bmiUpdater);
        weightSlider.addChangeListener(bmiUpdater);
        bmiUpdater.stateChanged(null);

        JPanel categoricalPanel = new JPanel(new GridLayout(0, 4, 5, 5));
        Map<String, JComboBox<String>> combos = new HashMap<>();

        int count = 0;
        for (String traitName : Human.TRAIT_VALUES.keySet()) {
            if (traitName.equals("Наличие веснушек") || traitName.equals("Телосложение")) {
                continue;
            }

            categoricalPanel.add(new JLabel(traitName + ":"));
            JComboBox<String> combo = new JComboBox<>(Human.TRAIT_VALUES.get(traitName));
            combos.put(traitName, combo);
            categoricalPanel.add(combo);

            count++;
            if (count % 2 == 0) {
                categoricalPanel.add(new JLabel(""));
                categoricalPanel.add(new JLabel(""));
            }
        }

        categoricalPanel.add(new JLabel("Наличие веснушек:"));
        JComboBox<String> frecklesCombo = new JComboBox<>(Human.TRAIT_VALUES.get("Наличие веснушек"));
        combos.put("Наличие веснушек", frecklesCombo);
        categoricalPanel.add(frecklesCombo);
        categoricalPanel.add(new JLabel(""));
        categoricalPanel.add(new JLabel(""));

        tabbedPane.addTab("Основное", infoPanel);
        tabbedPane.addTab("Физические параметры", new JScrollPane(physicalPanel));
        tabbedPane.addTab("Внешность", new JScrollPane(categoricalPanel));

        JButton createButton = new JButton("Создать");
        createButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Введите имя основателя!");
                return;
            }

            boolean isMale = genderCombo.getSelectedIndex() == 0;

            Map<String, Object> traits = new HashMap<>();

            traits.put("Рост", (double) heightSlider.getValue());
            traits.put("Вес", (double) weightSlider.getValue());
            traits.put("Мускулатура", muscleCombo.getSelectedItem());
            traits.put("Размер ушей", earCombo.getSelectedItem());

            for (String traitName : combos.keySet()) {
                traits.put(traitName, combos.get(traitName).getSelectedItem());
            }

            double heightM = heightSlider.getValue() / 100.0;
            double weight = weightSlider.getValue();
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

            engine.addCustomFounder(name, isMale, traits);
            dialog.dispose();
        });

        JButton cancelButton = new JButton("Отмена");
        cancelButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(createButton);
        buttonPanel.add(cancelButton);

        dialog.add(tabbedPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void updateStatus() {
        if (engine.isEvolutionRunning()) {
            statusLabel.setText("Эволюция запущена...");
            progressBar.setValue((engine.getCurrentYear() % 100));
        } else {
            statusLabel.setText(String.format(
                    "Готово | Год: %d | Поколений: %d | Живых: %d | Всего: %d | Ср.присп: %.2f",
                    engine.getCurrentYear(), engine.getMaxGeneration(), engine.getAlivePeopleCount(),
                    engine.getTotalPeopleCount(), engine.getAverageFitness()));
            progressBar.setValue(0);
        }
    }
}