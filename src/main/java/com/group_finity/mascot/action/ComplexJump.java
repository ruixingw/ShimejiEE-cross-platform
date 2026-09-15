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
 * A jump that can breed and/or scan for an affordance target, replacing
 * Jump/BreedJump/ScanJump via {@code Characteristics="Breed,Scan"}.
 */
public class ComplexJump extends Jump {

    private static final Logger log = Logger.getLogger(ComplexJump.class.getName());

    private static final String PARAMETER_CHARACTERISTICS = "Characteristics";
    private static final String DEFAULT_CHARACTERISTICS = "";

    private static final String PARAMETER_AFFORDANCE = "Affordance";
    private static final String DEFAULT_AFFORDANCE = "";

    public static final String PARAMETER_BEHAVIOUR = "Behaviour";
    private static final String DEFAULT_BEHAVIOUR = "";

    public static final String PARAMETER_TARGETBEHAVIOUR = "TargetBehaviour";
    private static final String DEFAULT_TARGETBEHAVIOUR = "";

    private static final String PARAMETER_TARGETLOOK = "TargetLook";
    private static final boolean DEFAULT_TARGETLOOK = false;

    private final Breed.Delegate breedDel = new Breed.Delegate(this);

    private WeakReference<Mascot> target;

    private boolean breedEnabled = false;
    private boolean scanEnabled = false;

    public ComplexJump(java.util.ResourceBundle schema, final List<Animation> animations, final VariableMap context) {
        super(schema, animations, context);
    }

    @Override
    public void init(final Mascot mascot) throws VariableException {
        super.init(mascot);

        for (String characteristic : getCharacteristics().split(",")) {
            if (characteristic.equals(getSchema().getString("Breed"))) {
                breedEnabled = true;
            }
            if (characteristic.equals(getSchema().getString("Scan"))) {
                scanEnabled = true;
            }
        }

        if (breedEnabled) {
            breedDel.validateBornCount();
            breedDel.validateBornInterval();
        }

        if (scanEnabled) {
            // cannot broadcast while scanning for an affordance
            getMascot().getAffordances().clear();

            if (getMascot().getManager() != null) {
                target = getMascot().getManager().getMascotWithAffordance(getAffordance());
            }
            putVariable(getSchema().getString("TargetX"), target != null && target.get() != null ? target.get().getAnchor().x : null);
            putVariable(getSchema().getString("TargetY"), target != null && target.get() != null ? target.get().getAnchor().y : null);
        }
    }

    @Override
    public boolean hasNext() throws VariableException {
        if (scanEnabled) {
            if (getMascot().getManager() == null) {
                return super.hasNext();
            }
            return super.hasNext()
                    && target != null
                    && target.get() != null
                    && target.get().getAffordances().contains(getAffordance());
        }
        return super.hasNext();
    }

    @Override
    protected void tick() throws LostGroundException, VariableException {
        if (scanEnabled) {
            // cannot broadcast while scanning for an affordance
            getMascot().getAffordances().clear();

            if (target == null || target.get() == null) {
                throw new LostGroundException();
            }

            putVariable(getSchema().getString("TargetX"), target.get().getAnchor().x);
            putVariable(getSchema().getString("TargetY"), target.get().getAnchor().y);
        }

        super.tick();

        if (breedEnabled && breedDel.isIntervalFrame() && breedDel.isAllowed()) {
            breedDel.breed();
        }

        if (scanEnabled && target != null && target.get() != null) {
            if (getMascot().getAnchor().equals(target.get().getAnchor())) {
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
    }

    private String getCharacteristics() throws VariableException {
        return eval(getSchema().getString(PARAMETER_CHARACTERISTICS), String.class, DEFAULT_CHARACTERISTICS);
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
