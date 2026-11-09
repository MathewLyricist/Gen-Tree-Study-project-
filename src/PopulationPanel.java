import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class PopulationPanel extends JPanel implements ChangeListener {
    private EvolutionEngine engine;
    private JTable populationTable;
    private PopulationTableModel tableModel;
    private JLabel statsLabel;
    private AtomicBoolean updateInProgress = new AtomicBoolean(false);
    private long lastUpdateTime = 0;

    public PopulationPanel(EvolutionEngine engine) {
        this.engine = engine;
        engine.addChangeListener(this);

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Текущая популяция"));
        setPreferredSize(new Dimension(300, 800));

        initializeComponents();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (updateInProgress.compareAndSet(false, true)) {
            try {
                tableModel.updateData(engine.getAllPeople());
                updateStatistics();
            } finally {
                updateInProgress.set(false);
            }
        }
    }

    static class PopulationTableModel extends AbstractTableModel {
        private List<Human> population;
        private final String[] columns = {"ID", "Имя", "Пол", "Возр", "Gen", "Присп.", "Статус"};

        public PopulationTableModel() {
            this.population = List.of();
        }

        public void updateData(List<Human> population) {
            SwingUtilities.invokeLater(() -> {
                this.population = population;
                fireTableDataChanged();
            });
        }

        public Human getHumanAt(int row) {
            return population.get(row);
        }

        @Override
        public int getRowCount() {
            return population.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Human human = population.get(row);

            return switch (column) {
                case 0 -> human.id;
                case 1 -> human.name;
                case 2 -> human.getGenderSymbol();
                case 3 -> human.age;
                case 4 -> human.generation;
                case 5 -> human.fitness;
                case 6 -> human.alive ? "Жив" : "Умер";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> Integer.class;
                case 1 -> String.class;
                case 2 -> String.class;
                case 3 -> Integer.class;
                case 4 -> Integer.class;
                case 5 -> Double.class;
                case 6 -> String.class;
                default -> Object.class;
            };
        }
    }

    private void initializeComponents() {
        tableModel = new PopulationTableModel();
        populationTable = new JTable(tableModel);

        populationTable.setRowHeight(25);
        populationTable.setAutoCreateRowSorter(true);
        populationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        populationTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        UIManager.put("Table.ascendingSortIcon", null);
        UIManager.put("Table.descendingSortIcon", null);

        TableColumnModel columnModel = populationTable.getColumnModel();
        columnModel.getColumn(0).setPreferredWidth(40);  // ID
        columnModel.getColumn(1).setPreferredWidth(120); // Имя
        columnModel.getColumn(2).setPreferredWidth(30);  // Пол
        columnModel.getColumn(3).setPreferredWidth(40);  // Возр
        columnModel.getColumn(4).setPreferredWidth(50);  // Gen
        columnModel.getColumn(5).setPreferredWidth(60);  // Приспособленность

        columnModel.getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value,
                        isSelected, hasFocus, row, column);

                if (!isSelected && value instanceof Double) {
                    double fitness = (Double) value;
                    if (fitness > 0.7) {
                        c.setBackground(new Color(200, 255, 200));
                    } else if (fitness > 0.4) {
                        c.setBackground(new Color(255, 255, 200));
                    } else {
                        c.setBackground(new Color(255, 200, 200));
                    }
                } else if (!isSelected) {
                    c.setBackground(Color.WHITE);
                }

                return c;
            }
        });

        populationTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value,
                        isSelected, hasFocus, row, column);

                if (!isSelected) {
                    int modelRow = table.convertRowIndexToModel(row);
                    Human human = tableModel.getHumanAt(modelRow);

                    if (human != null) {
                        if (!human.alive) {
                            c.setBackground(Color.LIGHT_GRAY);
                        } else if (human.isMale) {
                            c.setBackground(new Color(220, 240, 255));
                        } else {
                            c.setBackground(new Color(255, 220, 230));
                        }
                    }
                }

                return c;
            }
        });

        populationTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = populationTable.getSelectedRow();
                if (row >= 0) {
                    int modelRow = populationTable.convertRowIndexToModel(row);
                    Human human = tableModel.getHumanAt(modelRow);
                    engine.selectHuman(human);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(populationTable);

        statsLabel = new JLabel(" ");
        statsLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statsPanel.add(statsLabel);

        add(scrollPane, BorderLayout.CENTER);
        add(statsPanel, BorderLayout.SOUTH);

        updateStatistics();
    }

    private void updateStatistics() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime < 100) {
            return;
        }

        List<Human> alive = engine.getAlivePeople();
        List<Human> all = engine.getAllPeople();

        long males = alive.stream().filter(h -> h.isMale).count();
        long females = alive.size() - males;

        double avgAge = alive.stream().mapToInt(h -> h.age).average().orElse(0);
        double avgFitness = engine.getAverageFitness();
        double bestFitness = engine.getBestFitness();

        statsLabel.setText(String.format(
                "<html>Живых: <b>%d</b> (♂%d ♀%d) | Всего: <b>%d</b><br>" +
                        "Ср.возр: <b>%.1f</b> | Ср.присп: <b>%.2f</b> | Лучшая: <b>%.2f</b></html>",
                alive.size(), males, females, all.size(), avgAge, avgFitness, bestFitness
        ));

        lastUpdateTime = currentTime;
    }
}