import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class EvolutionSimulator {
    private static EvolutionEngine engine;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.setProperty("sun.java2d.opengl", "true");
        System.setProperty("sun.java2d.d3d", "true");

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Симулятор Эволюции");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1400, 850);

            engine = new EvolutionEngine();

            ControlPanel controlPanel = new ControlPanel(engine);
            GenealogyTreePanel treePanel = new GenealogyTreePanel(engine);
            DetailsPanel detailsPanel = new DetailsPanel(engine);
            PopulationPanel populationPanel = new PopulationPanel(engine);

            treePanel.setOptimizeRendering(controlPanel.isOptimizeRenderEnabled());

            controlPanel.getOptimizeRenderCheck().addActionListener(e -> {
                treePanel.setOptimizeRendering(controlPanel.isOptimizeRenderEnabled());
            });

            frame.setLayout(new BorderLayout());
            frame.add(controlPanel, BorderLayout.NORTH);

            JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    new JScrollPane(treePanel), detailsPanel);
            centerSplit.setDividerLocation(700);
            centerSplit.setResizeWeight(0.7);

            JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    centerSplit, populationPanel);
            mainSplit.setDividerLocation(900);
            mainSplit.setResizeWeight(0.7);

            frame.add(mainSplit, BorderLayout.CENTER);

            JLabel statusBar = new JLabel(" Готово. Добавьте основателей и запустите эволюцию.");
            statusBar.setBorder(BorderFactory.createEtchedBorder());
            frame.add(statusBar, BorderLayout.SOUTH);

            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    engine.shutdown();
                }

                @Override
                public void windowClosed(WindowEvent e) {
                    engine.shutdown();
                }
            });

            frame.setVisible(true);

            SwingUtilities.invokeLater(() -> {
                int cores = Runtime.getRuntime().availableProcessors();
                if (cores > 1) {
                    statusBar.setText(" Система использует " + cores + " ядер для ускорения вычислений.");
                }
            });
        });
    }
}