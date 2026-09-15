package com.group_finity.mascotnative.shared.swingui;

import com.group_finity.mascot.DebugUi;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.Tr;
import com.group_finity.mascot.behavior.UserBehavior;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * A small live stats window for a single mascot ("Reveal Statistics").
 * <p>
 * The original shimeji showed these stats in the debug window; the labels are
 * intentionally not localized, matching the original.
 */
public class DebugWindow implements DebugUi {

    private JFrame frame;
    private final JLabel[] valueLabels = new JLabel[Field.values().length];
    private boolean visibleRequested = false;
    private boolean disposed = false;
    private Runnable afterDispose = () -> {};

    private enum Field {
        IMAGE_SET(Tr.tr("DebugImageSet")),
        BEHAVIOR(Tr.tr("DebugBehaviour")),
        POSITION(Tr.tr("DebugPosition")),
        FACING(Tr.tr("DebugFacing")),
        ACTIVE_WINDOW(Tr.tr("DebugActiveWindow")),
        WINDOW_BOUNDS(Tr.tr("DebugWindowBounds")),
        WORK_AREA(Tr.tr("DebugWorkArea")),
        MASCOT_COUNT(Tr.tr("DebugMascotCount"));

        final String label;

        Field(String label) {
            this.label = label;
        }
    }

    public DebugWindow() {
        SwingUtilities.invokeLater(this::buildUi);
    }

    private void buildUi() {
        if (disposed) {
            return;
        }

        frame = new JFrame(Tr.tr("DebugStatistics"));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        var panel = new JPanel(new GridLayout(Field.values().length, 2, 8, 2));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        for (Field field : Field.values()) {
            panel.add(new JLabel(field.label + ":"));
            var value = new JLabel("-");
            valueLabels[field.ordinal()] = value;
            panel.add(value);
        }
        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);

        if (visibleRequested) {
            frame.setVisible(true);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> {
            visibleRequested = visible;
            if (frame != null) {
                frame.setVisible(visible);
            }
        });
    }

    @Override
    public void dispose() {
        SwingUtilities.invokeLater(() -> {
            disposed = true;
            if (frame != null) {
                frame.setVisible(false);
                frame.dispose();
                frame = null;
            }
            afterDispose.run();
            afterDispose = () -> {};
        });
    }

    @Override
    public void setAfterDisposeAction(Runnable action) {
        afterDispose = action == null ? () -> {} : action;
    }

    @Override
    public void update(Mascot mascot) {
        // snapshot the state off the manager thread
        String imageSet = mascot.getImageSet();
        String behavior = mascot.getBehavior() instanceof UserBehavior ub ? ub.getName() : String.valueOf(mascot.getBehavior());
        int x = mascot.getAnchor().x;
        int y = mascot.getAnchor().y;
        String facing = mascot.isLookRight() ? "right" : "left";
        var ie = mascot.getEnvironment().getActiveIE();
        boolean ieVisible = ie.isVisible();
        var ieRect = ie.toRectangle();
        final String ieTitle = ieVisible ? readIeTitle(mascot) : null;
        var workArea = mascot.getEnvironment().getWorkArea().toRectangle();
        int count = mascot.getManager() != null ? mascot.getManager().getCount(null) : 0;

        SwingUtilities.invokeLater(() -> {
            if (frame == null || disposed) {
                return;
            }
            valueLabels[Field.IMAGE_SET.ordinal()].setText(imageSet);
            valueLabels[Field.BEHAVIOR.ordinal()].setText(behavior);
            valueLabels[Field.POSITION.ordinal()].setText(x + ", " + y);
            valueLabels[Field.FACING.ordinal()].setText(facing);
            valueLabels[Field.ACTIVE_WINDOW.ordinal()].setText(!ieVisible ? "-" :
                    (ieTitle != null ? ieTitle : "(untitled)"));
            valueLabels[Field.WINDOW_BOUNDS.ordinal()].setText(!ieVisible ? "-" :
                    rectText(ieRect));
            valueLabels[Field.WORK_AREA.ordinal()].setText(rectText(workArea));
            valueLabels[Field.MASCOT_COUNT.ordinal()].setText(String.valueOf(count));
        });
    }

    private static String rectText(Rectangle r) {
        return r.x + ", " + r.y + " (" + r.width + " x " + r.height + ")";
    }

    private static String readIeTitle(Mascot mascot) {
        try {
            return mascot.getEnvironment().getActiveIETitle();
        } catch (Exception e) {
            return null;
        }
    }
}
