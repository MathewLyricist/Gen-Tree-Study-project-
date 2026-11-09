import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

class FamilyTreeDialog extends JDialog {
    private EvolutionEngine engine;
    private Human founder;
    private FamilyTreePanel treePanel;
    private JLabel statsLabel;

    public FamilyTreeDialog(Frame owner, EvolutionEngine engine, Human founder) {
        super(owner, "Полное семейное дерево: " + founder.name, true);
        this.engine = engine;
        this.founder = founder;

        setLayout(new BorderLayout());
        setSize(1400, 900);
        setLocationRelativeTo(owner);

        initializeComponents();
    }

    private void initializeComponents() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JLabel depthLabel = new JLabel("Глубина дерева:");
        JSlider depthSlider = new JSlider(1, 10, 3);
        depthSlider.setMajorTickSpacing(1);
        depthSlider.setMinorTickSpacing(1);
        depthSlider.setPaintTicks(true);
        depthSlider.setPaintLabels(true);
        depthSlider.setPreferredSize(new Dimension(200, 50));

        JLabel depthValueLabel = new JLabel("3 поколения");

        JCheckBox aliveOnlyCheck = new JCheckBox("Только живые");
        JCheckBox showSpousesCheck = new JCheckBox("Показать супругов", true);

        JButton refreshButton = new JButton("Обновить");
        JButton expandAllButton = new JButton("Показать всё дерево");
        JButton closeButton = new JButton("Закрыть");

        controlPanel.add(depthLabel);
        controlPanel.add(depthSlider);
        controlPanel.add(depthValueLabel);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(aliveOnlyCheck);
        controlPanel.add(showSpousesCheck);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(refreshButton);
        controlPanel.add(expandAllButton);
        controlPanel.add(closeButton);

        statsLabel = new JLabel(" ");
        statsLabel.setFont(new Font("Arial", Font.BOLD, 12));
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statsPanel.add(statsLabel);
        statsPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        List<Human> initialFamily = engine.getFamilyTree(founder, 3);
        treePanel = new FamilyTreePanel(initialFamily, founder);
        JScrollPane scrollPane = new JScrollPane(treePanel);

        depthSlider.addChangeListener(e -> {
            int depth = depthSlider.getValue();
            depthValueLabel.setText(depth + " поколения");
            updateFamilyTree(depth, aliveOnlyCheck.isSelected(), showSpousesCheck.isSelected());
        });

        ChangeListener filterListener = e -> {
            int depth = depthSlider.getValue();
            updateFamilyTree(depth, aliveOnlyCheck.isSelected(), showSpousesCheck.isSelected());
        };

        aliveOnlyCheck.addChangeListener(filterListener);
        showSpousesCheck.addChangeListener(filterListener);

        refreshButton.addActionListener(e -> {
            int depth = depthSlider.getValue();
            updateFamilyTree(depth, aliveOnlyCheck.isSelected(), showSpousesCheck.isSelected());
        });

        expandAllButton.addActionListener(e -> {
            List<Human> fullFamily = engine.getAllDescendants(founder);
            if (aliveOnlyCheck.isSelected()) {
                fullFamily = fullFamily.stream()
                        .filter(h -> h.alive)
                        .collect(Collectors.toList());
            }
            treePanel.updateFamilyMembers(fullFamily);
            updateStatistics(fullFamily);
            depthSlider.setValue(10);
        });

        closeButton.addActionListener(e -> dispose());

        add(controlPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(statsPanel, BorderLayout.SOUTH);

        updateStatistics(initialFamily);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }

    private void updateFamilyTree(int depth, boolean aliveOnly, boolean showSpouses) {
        List<Human> family = engine.getFamilyTree(founder, depth);

        if (aliveOnly) {
            family = family.stream()
                    .filter(h -> h.alive)
                    .collect(Collectors.toList());
        }

        if (!showSpouses) {
            List<Human> filtered = new ArrayList<>();
            for (Human person : family) {
                if (isAncestorOrDescendant(person, founder) || person == founder) {
                    filtered.add(person);
                }
            }
            family = filtered;
        }

        treePanel.updateFamilyMembers(family);
        updateStatistics(family);
    }

    private boolean isAncestorOrDescendant(Human person, Human root) {
        if (person == null || root == null) return false;

        Human current = root;
        while (current != null) {
            if (current == person) return true;
            if (current.father == person || current.mother == person) return true;
            current = current.father;
        }

        return checkDescendants(root, person);
    }

    private boolean checkDescendants(Human root, Human target) {
        if (root == null) return false;
        if (root == target) return true;

        for (Human child : root.children) {
            if (checkDescendants(child, target)) {
                return true;
            }
        }

        return false;
    }

    private void updateStatistics(List<Human> family) {
        if (family.isEmpty()) {
            statsLabel.setText("Нет членов семьи");
            return;
        }

        long aliveCount = family.stream().filter(h -> h.alive).count();
        long maleCount = family.stream().filter(h -> h.isMale).count();
        long femaleCount = family.size() - maleCount;
        int maxGeneration = family.stream().mapToInt(h -> h.generation).max().orElse(0);

        statsLabel.setText(String.format(
                "<html><b>Статистика семьи:</b> Всего членов: %d | Живых: %d | Мужчин: %d | Женщин: %d | Поколений: %d | Основатель: %s (ID: %d)</html>",
                family.size(), aliveCount, maleCount, femaleCount, maxGeneration + 1, founder.name, founder.id
        ));
    }
}

class FamilyTreePanel extends JPanel {
    private List<Human> familyMembers;
    private Human rootPerson;
    private Map<Human, Rectangle> nodeBounds = new HashMap<>();
    private Map<Human, Point> nodePositions = new HashMap<>();
    private double zoom = 1.0;
    private Point offset = new Point(50, 50);
    private final int NODE_WIDTH = 160;
    private final int NODE_HEIGHT = 70;
    private final int VERT_SPACING = 120;
    private final int HORIZ_SPACING = 60;

    // Для управления мышью
    private Point dragStart;
    private Point lastMousePos;
    private boolean isDragging = false;
    private boolean isRightDragging = false;
    private double zoomStep = 0.05;
    private double minZoom = 0.1;
    private double maxZoom = 5.0;

    // Для оптимизации
    private BufferedImage bufferImage = null;
    private boolean bufferDirty = true;

    public FamilyTreePanel(List<Human> familyMembers, Human rootPerson) {
        this.familyMembers = familyMembers;
        this.rootPerson = rootPerson;

        setBackground(new Color(248, 248, 255));
        setPreferredSize(new Dimension(3000, 2000));

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStart = e.getPoint();
                    lastMousePos = e.getPoint();
                    isDragging = true;
                    setCursor(new Cursor(Cursor.MOVE_CURSOR));
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    dragStart = e.getPoint();
                    lastMousePos = e.getPoint();
                    isRightDragging = true;
                    setCursor(new Cursor(Cursor.N_RESIZE_CURSOR));
                }
            }

            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    isDragging = false;
                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    isRightDragging = false;
                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }

            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    double oldZoom = zoom;
                    zoom = Math.min(maxZoom, Math.max(minZoom, zoom * 1.5));
                    Point mousePos = e.getPoint();
                    offset.x = (int)(mousePos.x - (mousePos.x - offset.x) * (zoom / oldZoom));
                    offset.y = (int)(mousePos.y - (mousePos.y - offset.y) * (zoom / oldZoom));
                    bufferDirty = true;
                    repaint();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (isDragging && dragStart != null) {
                    int dx = e.getX() - lastMousePos.x;
                    int dy = e.getY() - lastMousePos.y;

                    offset.x += dx;
                    offset.y += dy;

                    lastMousePos = e.getPoint();
                    bufferDirty = true;
                    repaint();
                } else if (isRightDragging && dragStart != null) {
                    int dy = lastMousePos.y - e.getY();
                    double scaleFactor = 1.0 + (dy * 0.005);

                    double oldZoom = zoom;
                    zoom = Math.min(maxZoom, Math.max(minZoom, zoom * scaleFactor));

                    if (oldZoom != zoom) {
                        offset.x = (int)(dragStart.x - (dragStart.x - offset.x) * (zoom / oldZoom));
                        offset.y = (int)(dragStart.y - (dragStart.y - offset.y) * (zoom / oldZoom));
                        bufferDirty = true;
                    }

                    lastMousePos = e.getPoint();
                    repaint();
                }
            }
        });

        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double oldZoom = zoom;
                Point mousePos = e.getPoint();
                int notches = e.getWheelRotation();

                if (notches < 0) {
                    zoom = Math.min(maxZoom, zoom * (1.0 + zoomStep));
                } else {
                    zoom = Math.max(minZoom, zoom * (1.0 - zoomStep));
                }

                if (oldZoom != zoom) {
                    offset.x = (int)(mousePos.x - (mousePos.x - offset.x) * (zoom / oldZoom));
                    offset.y = (int)(mousePos.y - (mousePos.y - offset.y) * (zoom / oldZoom));
                    bufferDirty = true;
                }
                repaint();
            }
        });
    }

    public void updateFamilyMembers(List<Human> newFamilyMembers) {
        this.familyMembers = newFamilyMembers;
        bufferDirty = true;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (bufferImage == null || bufferDirty) {
            createBufferImage();
        }

        g.drawImage(bufferImage, 0, 0, null);
    }

    private void createBufferImage() {
        bufferImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bufferImage.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        g2d.translate(offset.x, offset.y);
        g2d.scale(zoom, zoom);

        nodeBounds.clear();
        nodePositions.clear();

        layoutTree();
        drawConnections(g2d);
        drawNodes(g2d);

        g2d.setTransform(new AffineTransform());

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        g2d.drawString(String.format("Семейное дерево: %s (ID: %d)",
                rootPerson.name, rootPerson.id), 20, 30);

        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString(String.format("Масштаб: %.1fx | Всего членов: %d | Управление: ЛКМ-перемещение, ПКМ-масштаб, Колёсико-зум",
                zoom, familyMembers.size()), 20, 50);

        g2d.dispose();
        bufferDirty = false;
    }

    private void layoutTree() {
        if (familyMembers.isEmpty()) return;

        Map<Integer, List<Human>> byGeneration = new TreeMap<>();
        for (Human human : familyMembers) {
            byGeneration.computeIfAbsent(human.generation,
                    k -> new ArrayList<>()).add(human);
        }

        int startY = 100;
        int maxNodesInGeneration = 1;

        for (List<Human> generation : byGeneration.values()) {
            maxNodesInGeneration = Math.max(maxNodesInGeneration, generation.size());
        }

        int dynamicHorizSpacing = Math.max(HORIZ_SPACING, 800 / Math.max(1, maxNodesInGeneration));

        for (Map.Entry<Integer, List<Human>> entry : byGeneration.entrySet()) {
            int generation = entry.getKey();
            List<Human> generationPeople = entry.getValue();

            generationPeople.sort(Comparator.comparingInt(h -> h.id));

            int y = startY + generation * VERT_SPACING;

            int totalWidth = generationPeople.size() * (NODE_WIDTH + dynamicHorizSpacing) - dynamicHorizSpacing;
            int startX = 200;

            for (int i = 0; i < generationPeople.size(); i++) {
                Human human = generationPeople.get(i);
                int x = startX + i * (NODE_WIDTH + dynamicHorizSpacing);

                nodeBounds.put(human, new Rectangle(x, y, NODE_WIDTH, NODE_HEIGHT));
                nodePositions.put(human, new Point(x + NODE_WIDTH/2, y + NODE_HEIGHT/2));
            }
        }
    }

    private void drawConnections(Graphics2D g2d) {
        for (Human human : familyMembers) {
            if (human.father != null && familyMembers.contains(human.father)) {
                drawConnection(g2d, human.father, human, new Color(0, 0, 255, 180));
            }
            if (human.mother != null && familyMembers.contains(human.mother)) {
                drawConnection(g2d, human.mother, human, new Color(255, 0, 0, 180));
            }
        }
    }

    private void drawConnection(Graphics2D g2d, Human parent, Human child, Color color) {
        Point parentPos = nodePositions.get(parent);
        Point childPos = nodePositions.get(child);

        if (parentPos == null || childPos == null) return;

        int startX = parentPos.x;
        int startY = parentPos.y + NODE_HEIGHT/2;
        int endX = childPos.x;
        int endY = childPos.y - NODE_HEIGHT/2;

        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int ctrlY1 = startY + Math.abs(endY - startY) / 3;
        int ctrlY2 = endY - Math.abs(endY - startY) / 3;

        g2d.drawLine(startX, startY, startX, ctrlY1);
        g2d.drawLine(startX, ctrlY1, endX, ctrlY2);
        g2d.drawLine(endX, ctrlY2, endX, endY);

        drawArrow(g2d, endX, endY, startX, startY, color);
    }

    private void drawArrow(Graphics2D g2d, int x, int y, int fromX, int fromY, Color color) {
        int arrowSize = 8;
        double angle = Math.atan2(y - fromY, x - fromX);

        int x1 = (int)(x - arrowSize * Math.cos(angle - Math.PI / 6));
        int y1 = (int)(y - arrowSize * Math.sin(angle - Math.PI / 6));
        int x2 = (int)(x - arrowSize * Math.cos(angle + Math.PI / 6));
        int y2 = (int)(y - arrowSize * Math.sin(angle + Math.PI / 6));

        g2d.setColor(color);
        g2d.fillPolygon(new int[]{x, x1, x2}, new int[]{y, y1, y2}, 3);

        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(0.8f));
        g2d.drawPolygon(new int[]{x, x1, x2}, new int[]{y, y1, y2}, 3);
    }

    private void drawNodes(Graphics2D g2d) {
        for (Map.Entry<Human, Rectangle> entry : nodeBounds.entrySet()) {
            Human human = entry.getKey();
            Rectangle rect = entry.getValue();
            drawHumanNode(g2d, human, rect.x, rect.y);
        }
    }

    private void drawHumanNode(Graphics2D g2d, Human human, int x, int y) {
        Color bgColor;
        if (!human.alive) {
            bgColor = new Color(200, 200, 200, 220);
        } else if (human == rootPerson) {
            bgColor = new Color(255, 255, 150, 240); // Корень дерева
        } else if (human.isMale) {
            bgColor = new Color(173, 216, 230, 240);
        } else {
            bgColor = new Color(255, 182, 193, 240);
        }

        g2d.setColor(new Color(100, 100, 100, 80));
        g2d.fillRoundRect(x + 3, y + 3, NODE_WIDTH, NODE_HEIGHT, 15, 15);

        g2d.setColor(bgColor);
        g2d.fillRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, 15, 15);

        g2d.setColor(human == rootPerson ? Color.RED :
                (human.isFounder() ? new Color(0, 100, 0) : Color.BLACK));
        g2d.setStroke(new BasicStroke(human == rootPerson ? 3.0f :
                (human.isFounder() ? 2.5f : 2.0f)));
        g2d.drawRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, 15, 15);

        g2d.setFont(new Font("Arial", Font.PLAIN, 11));

        String displayName = human.name;
        if (displayName.length() > 18) {
            displayName = displayName.substring(0, 16) + "...";
        }
        g2d.drawString(displayName, x + 8, y + 18);

        String info = human.getGenderSymbol();
        if (human.alive) {
            info += " " + human.age + " лет";
        } else {
            info += " ✝ " + (human.deathAge == -1 ? "?" : human.deathAge) + " лет";
        }
        g2d.drawString(info, x + 8, y + 35);

        g2d.setColor(Color.DARK_GRAY);
        g2d.drawString("ID:" + human.id + " Gen:" + human.generation, x + 8, y + 52);

        int fitnessBar = (int)(human.fitness * (NODE_WIDTH - 20));
        if (human.fitness > 0.7) {
            g2d.setColor(new Color(0, 180, 0));
        } else if (human.fitness > 0.4) {
            g2d.setColor(new Color(255, 165, 0));
        } else {
            g2d.setColor(Color.RED);
        }
        g2d.fillRect(x + 8, y + NODE_HEIGHT - 10, fitnessBar, 6);

        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawRect(x + 8, y + NODE_HEIGHT - 10, NODE_WIDTH - 20, 6);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 10));
        g2d.drawString(String.format("%.0f%%", human.fitness * 100),
                x + NODE_WIDTH - 30, y + NODE_HEIGHT - 4);
    }
}