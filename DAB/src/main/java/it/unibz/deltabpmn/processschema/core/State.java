package it.unibz.deltabpmn.processschema.core;

import it.unibz.deltabpmn.dataschema.elements.Constant;
import it.unibz.deltabpmn.dataschema.elements.Sort;
import it.unibz.deltabpmn.dataschema.core.SystemSorts;

/**
 * Stores predefined states of process components such as tasks, events and blocks. Each state is treated as a {@link Constant}.
 */
public enum State implements Constant {
    IDLE {
        @Override
        public String getName() {
            return "Idle";
        }
    },

    ENABLED {
        @Override
        public String getName() {
            return "Enabled";
        }
    },

    ACTIVE {
        @Override
        public String getName() {
            return "Active";
        }
    },

    COMPLETED {
        @Override
        public String getName() {
            return "Completed";
        }
    },

    ACTIVE_SINGLE {
        @Override
        public String getName() {
            return "Active_OnePath";
        }
    },

    ACTIVE_ALL {
        @Override
        public String getName() {
            return "Active_AllPath";
        }
    };

    @Override
    public Sort getSort() {
        return SystemSorts.STRING;
    }

    @Override
    public String getMCMTDeclaration() {
        return ":smt (define " + getName() + " ::" + getSort().getSortName() + ")\n";
    }

}
