import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.*;

class GenealogyTreePanel extends JPanel implements ChangeListener {
    private EvolutionEngine engine;
    private double zoom = 1.0;
    private Point offset = new Point(50, 50);
    private Point dragStart;
    private Point lastMousePos;
    private final int NODE_WIDTH = 160;
    private final int NODE_HEIGHT = 70;
    private final int VERT_SPACING = 120;
    private final int HORIZ_SPACING = 60;
    private Map<Human, Rectangle> nodeBounds = new HashMap<>();
    private Map<Human, Point> nodeCenters = new HashMap<>();

    private JButton zoomInButton;
    private JButton zoomOutButton;
    private JButton resetZoomButton;
    private JButton fitToScreenButton;

    private boolean optimizeRendering = true;
    private int lastRepaintCount = 0;
    private long lastRepaintTime = 0;
    private BufferedImage bufferImage = null;
    private boolean bufferDirty = true;
    private final Object bufferLock = new Object();

    private boolean isDragging = false;
    private boolean isRightDragging = false;
    private double zoomStep = 0.05;
    private double minZoom = 0.1;
    private double maxZoom = 5.0;

    public GenealogyTreePanel(EvolutionEngine engine) {
        this.engine = engine;
        engine.addChangeListener(this);
        setBackground(new Color(248, 248, 255));
        setPreferredSize(new Dimension(3000, 2500));
        setLayout(null);

        createControlPanel();

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStart = e.getPoint();
                    lastMousePos = e.getPoint();
                    isDragging = true;

                    if (!checkNodeClick(e.getX(), e.getY())) {
                        setCursor(new Cursor(Cursor.MOVE_CURSOR));
                    }
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    dragStart = e.getPoint();
                    lastMousePos = e.getPoint();
                    isRightDragging = true;
                    setCursor(new Cursor(Cursor.N_RESIZE_CURSOR)); // Курсор изменения размера
                }
            }

            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStart = null;
                    isDragging = false;
                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    markBufferDirty();
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    dragStart = null;
                    isRightDragging = false;
                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    markBufferDirty();
                }
            }

            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    double oldZoom = zoom;
                    zoom = Math.min(maxZoom, Math.max(minZoom, zoom * 1.5));
                    Point mousePos = e.getPoint();
                    offset.x = (int)(mousePos.x - (mousePos.x - offset.x) * (zoom / oldZoom));
                    offset.y = (int)(mousePos.y - (mousePos.y - offset.y) * (zoom / oldZoom));
                    markBufferDirty();
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

                    if (Math.abs(dx) > 2 || Math.abs(dy) > 2) {
                        optimizedRepaint();
                    }
                } else if (isRightDragging && dragStart != null) {
                    int dy = lastMousePos.y - e.getY();
                    double scaleFactor = 1.0 + (dy * 0.005);

                    double oldZoom = zoom;
                    zoom = Math.min(maxZoom, Math.max(minZoom, zoom * scaleFactor));

                    if (oldZoom != zoom) {
                        offset.x = (int)(dragStart.x - (dragStart.x - offset.x) * (zoom / oldZoom));
                        offset.y = (int)(dragStart.y - (dragStart.y - offset.y) * (zoom / oldZoom));
                        markBufferDirty();
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
                    markBufferDirty();
                }
                repaint();
            }
        });
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        markBufferDirty();

        if (optimizeRendering) {
            SwingUtilities.invokeLater(() -> {
                lastRepaintCount++;
                if (lastRepaintCount > 5) {
                    repaint();
                    lastRepaintCount = 0;
                }
            });
        } else {
            repaint();
        }
    }

    public void optimizedRepaint() {
        if (optimizeRendering) {
            lastRepaintCount++;
            if (lastRepaintCount > 2) {
                repaint();
                lastRepaintCount = 0;
            }
        } else {
            repaint();
        }
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        markBufferDirty();

        if (fitToScreenButton != null) {
            fitToScreenButton.getParent().setBounds(10, 10, 180, 160);
        }
    }

    public void setOptimizeRendering(boolean optimize) {
        this.optimizeRendering = optimize;
        markBufferDirty();
        repaint();
    }

    private void createControlPanel() {
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setOpaque(false);
        controlPanel.setBounds(10, 10, 180, 160);

        Font buttonFont = new Font("Arial", Font.BOLD, 16);
        Font smallFont = new Font("Arial", Font.PLAIN, 12);

        zoomInButton = new JButton("+ Увеличить");
        zoomInButton.setFont(smallFont);
        zoomInButton.setFocusPainted(false);
        zoomInButton.setBackground(new Color(220, 240, 255));
        zoomInButton.setBorder(BorderFactory.createRaisedBevelBorder());
        zoomInButton.setMaximumSize(new Dimension(180, 40));
        zoomInButton.addActionListener(e -> {
            double oldZoom = zoom;
            zoom = Math.min(maxZoom, zoom * (1.0 + zoomStep));
            Point center = new Point(getWidth()/2, getHeight()/2);
            offset.x = (int)(center.x - (center.x - offset.x) * (zoom / oldZoom));
            offset.y = (int)(center.y - (center.y - offset.y) * (zoom / oldZoom));
            markBufferDirty();
            repaint();
        });

        zoomOutButton = new JButton("- Уменьшить");
        zoomOutButton.setFont(smallFont);
        zoomOutButton.setFocusPainted(false);
        zoomOutButton.setBackground(new Color(220, 240, 255));
        zoomOutButton.setBorder(BorderFactory.createRaisedBevelBorder());
        zoomOutButton.setMaximumSize(new Dimension(180, 40));
        zoomOutButton.addActionListener(e -> {
            double oldZoom = zoom;
            zoom = Math.max(minZoom, zoom * (1.0 - zoomStep));
            Point center = new Point(getWidth()/2, getHeight()/2);
            offset.x = (int)(center.x - (center.x - offset.x) * (zoom / oldZoom));
            offset.y = (int)(center.y - (center.y - offset.y) * (zoom / oldZoom));
            markBufferDirty();
            repaint();
        });

        resetZoomButton = new JButton("1:1 Сбросить масштаб");
        resetZoomButton.setFont(smallFont);
        resetZoomButton.setFocusPainted(false);
        resetZoomButton.setBackground(new Color(255, 240, 220));
        resetZoomButton.setBorder(BorderFactory.createRaisedBevelBorder());
        resetZoomButton.setMaximumSize(new Dimension(180, 40));
        resetZoomButton.addActionListener(e -> {
            zoom = 1.0;
            offset.x = 50;
            offset.y = 50;
            markBufferDirty();
            repaint();
        });

        fitToScreenButton = new JButton("⛶ Подогнать под экран");
        fitToScreenButton.setFont(smallFont);
        fitToScreenButton.setFocusPainted(false);
        fitToScreenButton.setBackground(new Color(220, 255, 220));
        fitToScreenButton.setBorder(BorderFactory.createRaisedBevelBorder());
        fitToScreenButton.setMaximumSize(new Dimension(180, 40));
        fitToScreenButton.addActionListener(e -> {
            fitToScreen();
            markBufferDirty();
        });

        controlPanel.add(zoomInButton);
        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(zoomOutButton);
        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(resetZoomButton);
        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(fitToScreenButton);

        add(controlPanel);
    }

    private boolean checkNodeClick(int x, int y) {
        if (zoomInButton.getBounds().contains(x, y) ||
                zoomOutButton.getBounds().contains(x, y) ||
                resetZoomButton.getBounds().contains(x, y) ||
                fitToScreenButton.getBounds().contains(x, y)) {
            return true;
        }

        for (Map.Entry<Human, Rectangle> entry : nodeBounds.entrySet()) {
            Rectangle rect = entry.getValue();
            int nodeX = (int)(rect.x * zoom + offset.x);
            int nodeY = (int)(rect.y * zoom + offset.y);
            int nodeWidth = (int)(rect.width * zoom);
            int nodeHeight = (int)(rect.height * zoom);

            if (x >= nodeX && x <= nodeX + nodeWidth &&
                    y >= nodeY && y <= nodeY + nodeHeight) {
                engine.selectHuman(entry.getKey());
                markBufferDirty();
                repaint();
                return true;
            }
        }
        return false;
    }

    private void markBufferDirty() {
        synchronized (bufferLock) {
            bufferDirty = true;
        }
    }

    private void fitToScreen() {
        List<Human> allPeople = engine.getAllPeople();
        if (allPeople.isEmpty()) return;

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        Map<Integer, List<Human>> byGeneration = new TreeMap<>();
        for (Human human : allPeople) {
            byGeneration.computeIfAbsent(human.generation,
                    k -> new ArrayList<>()).add(human);
        }

        int startY = 80;
        for (Map.Entry<Integer, List<Human>> entry : byGeneration.entrySet()) {
            int generation = entry.getKey();
            List<Human> generationPeople = entry.getValue();

            int y = startY + generation * VERT_SPACING;
            int startX = 150;

            for (int i = 0; i < generationPeople.size(); i++) {
                int x = startX + i * (NODE_WIDTH + HORIZ_SPACING);

                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x + NODE_WIDTH);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y + NODE_HEIGHT);
            }
        }

        int padding = 100;
        minX -= padding;
        maxX += padding;
        minY -= padding;
        maxY += padding;

        int treeWidth = maxX - minX;
        int treeHeight = maxY - minY;

        int panelWidth = getWidth();
        int panelHeight = getHeight();

        double scaleX = (double) panelWidth / treeWidth;
        double scaleY = (double) panelHeight / treeHeight;
        zoom = Math.min(scaleX, scaleY) * 0.9;

        offset.x = (int) ((panelWidth - treeWidth * zoom) / 2 - minX * zoom);
        offset.y = (int) ((panelHeight - treeHeight * zoom) / 2 - minY * zoom);

        markBufferDirty();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        long currentTime = System.currentTimeMillis();

        if (optimizeRendering && currentTime - lastRepaintTime < 16) {
            return;
        }

        super.paintComponent(g);
        lastRepaintTime = currentTime;

        synchronized (bufferLock) {
            if (bufferImage == null ||
                    bufferImage.getWidth() != getWidth() ||
                    bufferImage.getHeight() != getHeight() ||
                    bufferDirty) {

                if (bufferImage != null) {
                    bufferImage.flush();
                }
                bufferImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);

                Graphics2D bufferGraphics = bufferImage.createGraphics();
                renderToGraphics(bufferGraphics);
                bufferGraphics.dispose();

                bufferDirty = false;
            }

            g.drawImage(bufferImage, 0, 0, null);
        }

        paintChildren(g);
    }

    private void renderToGraphics(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        g2d.translate(offset.x, offset.y);
        g2d.scale(zoom, zoom);

        nodeBounds.clear();
        nodeCenters.clear();

        drawNodes(g2d);
        drawConnections(g2d);

        g2d.setTransform(new AffineTransform());

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        String info = String.format("Масштаб: %.2fx | Положение: (%d, %d) | Год: %d | Всего особей: %d",
                zoom, offset.x, offset.y, engine.getCurrentYear(), engine.getTotalPeopleCount());
        g2d.drawString(info, 60, 40);

        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        String controls = "Управление: ЛКМ - перетаскивание | ПКМ - масштабирование | Колёсико - масштабирование | Двойной клик - приближение";
        g2d.drawString(controls, 60, 60);
    }

    private void drawConnections(Graphics2D g2d) {
        List<Human> allPeople = engine.getAllPeople();
        List<Connection> connections = new ArrayList<>();

        for (Human human : allPeople) {
            if (human.father != null) {
                connections.add(new Connection(human.father, human, new Color(0, 100, 200, 200), "father"));
            }
            if (human.mother != null) {
                connections.add(new Connection(human.mother, human, new Color(200, 50, 50, 200), "mother"));
            }
        }

        connections.sort((c1, c2) -> Integer.compare(c1.parent.generation, c2.parent.generation));

        for (Connection conn : connections) {
            Point parentCenter = nodeCenters.get(conn.parent);
            Point childCenter = nodeCenters.get(conn.child);

            if (parentCenter != null && childCenter != null) {
                drawConnectionLine(g2d, parentCenter, childCenter, conn.color, conn.type);
            }
        }
    }

    private void drawConnectionLine(Graphics2D g2d, Point from, Point to, Color color, String type) {
        g2d.setColor(color);

        if (type.equals("father")) {
            g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        } else {
            g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0, new float[]{6, 4}, 0));
        }

        int startX = from.x;
        int startY = from.y + NODE_HEIGHT/2 + 10;
        int endX = to.x;
        int endY = to.y - 10;

        int controlY1 = startY + Math.abs(endY - startY) / 3;
        int controlY2 = endY - Math.abs(endY - startY) / 3;

        if (Math.abs(startY - endY) < 50) {
            controlY1 = startY + 60;
            controlY2 = endY - 60;
        }

        CubicCurve2D curve = new CubicCurve2D.Float(
                startX, startY,
                startX, controlY1,
                endX, controlY2,
                endX, endY
        );

        g2d.draw(curve);
        drawArrow(g2d, endX, endY, startX, startY, color);
        drawParentMarker(g2d, startX, startY, type);
    }

    private void drawArrow(Graphics2D g2d, int x, int y, int fromX, int fromY, Color color) {
        int arrowSize = 10;
        double dx = x - fromX;
        double dy = y - fromY;
        double angle = Math.atan2(dy, dx);

        int x1 = (int)(x - arrowSize * Math.cos(angle - Math.PI/6));
        int y1 = (int)(y - arrowSize * Math.sin(angle - Math.PI/6));
        int x2 = (int)(x - arrowSize * Math.cos(angle + Math.PI/6));
        int y2 = (int)(y - arrowSize * Math.sin(angle + Math.PI/6));

        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 255));
        int[] xPoints = {x, x1, x2};
        int[] yPoints = {y, y1, y2};
        g2d.fillPolygon(xPoints, yPoints, 3);

        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(0.8f));
        g2d.drawPolygon(xPoints, yPoints, 3);
    }

    private void drawParentMarker(Graphics2D g2d, int x, int y, String type) {
        int markerSize = 6;
        g2d.setColor(type.equals("father") ? Color.BLUE : Color.RED);
        g2d.fillOval(x - markerSize/2, y - markerSize/2, markerSize, markerSize);
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.drawOval(x - markerSize/2, y - markerSize/2, markerSize, markerSize);
    }

    private void drawNodes(Graphics2D g2d) {
        List<Human> allPeople = engine.getAllPeople();

        Map<Integer, List<Human>> byGeneration = new TreeMap<>();
        for (Human human : allPeople) {
            byGeneration.computeIfAbsent(human.generation,
                    k -> new ArrayList<>()).add(human);
        }

        int startY = 100;

        for (Map.Entry<Integer, List<Human>> entry : byGeneration.entrySet()) {
            int generation = entry.getKey();
            List<Human> generationPeople = entry.getValue();

            generationPeople.sort(Comparator.comparingInt(h -> h.id));

            int y = startY + generation * VERT_SPACING;

            int totalWidth = generationPeople.size() * (NODE_WIDTH + HORIZ_SPACING);
            int startX = 180;

            for (int i = 0; i < generationPeople.size(); i++) {
                Human human = generationPeople.get(i);
                int x = startX + i * (NODE_WIDTH + HORIZ_SPACING);

                drawHumanNode(g2d, human, x, y);
                nodeBounds.put(human, new Rectangle(x, y, NODE_WIDTH, NODE_HEIGHT));
                nodeCenters.put(human, new Point(x + NODE_WIDTH/2, y + NODE_HEIGHT/2));
            }

            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 16));
            g2d.drawString("Поколение " + generation, 30, y + NODE_HEIGHT/2 + 5);

            g2d.setColor(new Color(200, 200, 200, 120));
            g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                    0, new float[]{8, 8}, 0));
            g2d.drawLine(startX - 30, y + NODE_HEIGHT/2,
                    startX + totalWidth + 30, y + NODE_HEIGHT/2);
        }
    }

    private void drawHumanNode(Graphics2D g2d, Human human, int x, int y) {
        Color bgColor;
        if (!human.alive) {
            bgColor = new Color(200, 200, 200, 220);
        } else if (human == engine.getSelectedHuman()) {
            bgColor = new Color(255, 255, 150, 240);
        } else if (human.isMale) {
            bgColor = new Color(173, 216, 230, 240);
        } else {
            bgColor = new Color(255, 182, 193, 240);
        }

        g2d.setColor(new Color(100, 100, 100, 80));
        g2d.fillRoundRect(x + 3, y + 3, NODE_WIDTH, NODE_HEIGHT, 15, 15);

        g2d.setColor(bgColor);
        g2d.fillRoundRect(x, y, NODE_WIDTH, NODE_HEIGHT, 15, 15);

        g2d.setColor(human == engine.getSelectedHuman() ? Color.RED :
                (human.isFounder() ? new Color(0, 100, 0) : Color.BLACK));
        g2d.setStroke(new BasicStroke(human == engine.getSelectedHuman() ? 3.0f :
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
            if (human.deathAge == -1) {
                info += " ✝ ? лет";
            } else {
                info += " ✝ " + human.deathAge + " лет";
            }
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

    private class Connection {
        Human parent;
        Human child;
        Color color;
        String type;

        Connection(Human parent, Human child, Color color, String type) {
            this.parent = parent;
            this.child = child;
            this.color = color;
            this.type = type;
        }
    }
}