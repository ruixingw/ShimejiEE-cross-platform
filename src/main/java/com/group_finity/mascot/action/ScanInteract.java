package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.Tr;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.exception.BehaviorInstantiationException;
import com.group_finity.mascot.exception.CantBeAliveException;
import com.group_finity.mascot.exception.LostGroundException;
import com.group_finity.mascot.exception.VariableException;
import com.group_finity.mascot.script.VariableMap;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stands still and interacts with the nearest mascot broadcasting a
 * matching affordance, triggering behaviours on the last animation frame.
 */
public class ScanInteract extends BorderedAction {

    private static final Logger log = Logger.getLogger(ScanInteract.class.getName());

    private static final String PARAMETER_AFFORDANCE = "Affordance";
    private static final String DEFAULT_AFFORDANCE = "";

    public static final String PARAMETER_BEHAVIOUR = "Behaviour";
    private static final String DEFAULT_BEHAVIOUR = "";

    public static final String PARAMETER_TARGETBEHAVIOUR = "TargetBehaviour";
    private static final String DEFAULT_TARGETBEHAVIOUR = "";

    private static final String PARAMETER_TARGETLOOK = "TargetLook";
    private static final boolean DEFAULT_TARGETLOOK = false;

    private WeakReference<Mascot> target;

    public ScanInteract(java.util.ResourceBundle schema, final List<Animation> animations, final VariableMap context) {
        super(schema, animations, context);
    }

    @Override
    public void init(final Mascot mascot) throws VariableException {
        super.init(mascot);

        // cannot broadcast while scanning for an affordance
        getMascot().getAffordances().clear();

        putVariable(getSchema().getString("TargetX"), null);
        putVariable(getSchema().getString("TargetY"), null);
    }

    @Override
    public boolean hasNext() throws VariableException {
        final boolean intime = getTime() < getAnimation().getDuration();
        return super.hasNext() && intime;
    }

    @Override
    protected void tick() throws LostGroundException, VariableException {
        super.tick();

        // cannot broadcast while scanning for an affordance
        getMascot().getAffordances().clear();

        if ((getBorder() != null) && !getBorder().isOn(getMascot().getAnchor())) {
            log.log(Level.INFO, "Lost Ground ({0},{1})", new Object[]{getMascot(), this});
            throw new LostGroundException();
        }

        // refresh target
        if (getMascot().getManager() != null
                && (target == null || target.get() == null || !target.get().getAffordances().contains(getAffordance()))) {
            target = getMascot().getManager().getMascotWithAffordance(getAffordance());
        }

        putVariable(getSchema().getString("TargetX"), target != null && target.get() != null ? target.get().getAnchor().x : null);
        putVariable(getSchema().getString("TargetY"), target != null && target.get() != null ? target.get().getAnchor().y : null);

        if (target != null && target.get() != null && target.get().getAffordances().contains(getAffordance())) {
            if (getMascot().getAnchor().x != target.get().getAnchor().x) {
                getMascot().setLookRight(getMascot().getAnchor().x < target.get().getAnchor().x);
            }

            getAnimation().next(getMascot(), getTime());

            if ((getTime() == getAnimation().getDuration() - 1 || getAnimation().getDuration() == 1)
                    && !getBehavior().trim().isEmpty()) {
                try {
                    getMascot().setBehavior(getMascot().getOwnImageSet().getConfiguration().buildBehavior(getBehavior(), getMascot()));
                    if (!getTargetBehavior().trim().isEmpty()) {
                        target.get().setBehavior(target.get().getOwnImageSet().getConfiguration().buildBehavior(getTargetBehavior(), target.get()));
                    }
                    if (getTargetLook() && target.get().isLookRight() == getMascot().isLookRight()) {
                        target.get().setLookRight(!getMascot().isLookRight());
                    }
                } catch (final NullPointerException | BehaviorInstantiationException | CantBeAliveException e) {
                    log.log(Level.SEVERE, "Fatal Exception", e);
                    throw new VariableException(Tr.tr("FailedSetBehaviourErrorMessage"), e);
                }
            }
        }
    }

    private String getAffordance() throws VariableException {
        return eval(getSchema().getString(PARAMETER_AFFORDANCE), String.class, DEFAULT_AFFORDANCE);
    }

    private String getBehavior() throws VariableException {
        return eval(getSchema().getString(PARAMETER_BEHAVIOUR), String.class, DEFAULT_BEHAVIOUR);
    }

    private String getTargetBehavior() throws VariableException {
        return eval(getSchema().getString(PARAMETER_TARGETBEHAVIOUR), String.class, DEFAULT_TARGETBEHAVIOUR);
    }

    private boolean getTargetLook() throws VariableException {
        return eval(getSchema().getString(PARAMETER_TARGETLOOK), Boolean.class, DEFAULT_TARGETLOOK);
    }
}
