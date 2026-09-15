package com.group_finity.mascot.action;

import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.animation.Animation;
import com.group_finity.mascot.exception.BehaviorInstantiationException;
import com.group_finity.mascot.exception.CantBeAliveException;
import com.group_finity.mascot.exception.LostGroundException;
import com.group_finity.mascot.exception.VariableException;
import com.group_finity.mascot.script.VariableMap;
import com.group_finity.mascot.Tr;

import java.awt.Point;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Jumps to the nearest mascot broadcasting a matching affordance
 * (the jumping variant of {@link ScanMove}).
 */
public class ScanJump extends ActionBase {

    private static final Logger log = Logger.getLogger(ScanJump.class.getName());

    private static final String PARAMETER_AFFORDANCE = "Affordance";
    private static final String DEFAULT_AFFORDANCE = "";

    public static final String PARAMETER_BEHAVIOUR = "Behaviour";
    private static final String DEFAULT_BEHAVIOUR = "";

    public static final String PARAMETER_TARGETBEHAVIOUR = "TargetBehaviour";
    private static final String DEFAULT_TARGETBEHAVIOUR = "";

    private static final String PARAMETER_TARGETLOOK = "TargetLook";
    private static final boolean DEFAULT_TARGETLOOK = false;

    //An Action Attribute is already named Velocity
    public static final String PARAMETER_VELOCITY = "VelocityParam";
    private static final double DEFAULT_VELOCITY = 20.0;

    public static final String VARIABLE_VELOCITYX = "VelocityX";
    public static final String VARIABLE_VELOCITYY = "VelocityY";

    private WeakReference<Mascot> target;

    public ScanJump(java.util.ResourceBundle schema, final List<Animation> animations, final VariableMap params) {
        super(schema, animations, params);
    }

    @Override
    public void init(final Mascot mascot) throws VariableException {
        super.init(mascot);

        // cannot broadcast while scanning for an affordance
        getMascot().getAffordances().clear();

        if (getMascot().getManager() != null) {
            target = getMascot().getManager().getMascotWithAffordance(getAffordance());
        }
        putVariable(getSchema().getString("TargetX"), target != null && target.get() != null ? target.get().getAnchor().x : null);
        putVariable(getSchema().getString("TargetY"), target != null && target.get() != null ? target.get().getAnchor().y : null);
    }

    @Override
    public boolean hasNext() throws VariableException {
        if (getMascot().getManager() == null) {
            return super.hasNext();
        }

        return super.hasNext()
                && target != null
                && target.get() != null
                && target.get().getAffordances().contains(getAffordance());
    }

    @Override
    protected void tick() throws LostGroundException, VariableException {
        // cannot broadcast while scanning for an affordance
        getMascot().getAffordances().clear();

        if (target == null || target.get() == null) {
            return;
        }

        final int targetX = target.get().getAnchor().x;
        final int targetY = target.get().getAnchor().y;

        putVariable(getSchema().getString("TargetX"), targetX);
        putVariable(getSchema().getString("TargetY"), targetY);

        getMascot().setLookRight(getMascot().getAnchor().x < targetX);

        final double distanceX = targetX - getMascot().getAnchor().x;
        final double distanceY = targetY - getMascot().getAnchor().y - Math.abs(distanceX) / 2;

        final double distance = Math.sqrt(distanceX * distanceX + distanceY * distanceY);

        final double velocity = getVelocity();

        if (distance != 0) {
            final int velocityX = (int) (velocity * distanceX / distance);
            final int velocityY = (int) (velocity * distanceY / distance);

            putVariable(getSchema().getString(VARIABLE_VELOCITYX), velocityX);
            putVariable(getSchema().getString(VARIABLE_VELOCITYY), velocityY);

            getMascot().getAnchor().translate(velocityX, velocityY);
            getAnimation().next(getMascot(), getTime());
        }

        if (distance <= velocity) {
            getMascot().setAnchor(new Point(targetX, targetY));

            try {
                getMascot().setBehavior(getMascot().getOwnImageSet().getConfiguration().buildBehavior(getBehavior()));
                target.get().setBehavior(target.get().getOwnImageSet().getConfiguration().buildBehavior(getTargetBehavior()));
                if (getTargetLook() && target.get().isLookRight() == getMascot().isLookRight()) {
                    target.get().setLookRight(!getMascot().isLookRight());
                }
            } catch (final NullPointerException | BehaviorInstantiationException | CantBeAliveException e) {
                log.log(Level.SEVERE, "Fatal Exception", e);
                throw new VariableException(Tr.tr("FailedSetBehaviourErrorMessage"), e);
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

    private double getVelocity() throws VariableException {
        return eval(getSchema().getString(PARAMETER_VELOCITY), Number.class, DEFAULT_VELOCITY).doubleValue();
    }
}
