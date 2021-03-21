package it.unibz.deltabpmn.dataschema.core;

import it.unibz.deltabpmn.dataschema.core.SystemSorts;
import it.unibz.deltabpmn.dataschema.elements.CaseVariable;
import it.unibz.deltabpmn.dataschema.elements.Sort;

public enum SystemVariables implements CaseVariable {
    EMPTY {
        public String getName() {
            return "lifecycleEmpty";
        }

        @Override
        public boolean isOneCase() {
            return true;
        }

        @Override
        public int getLifeCycle() {
            return 1;
        }

        @Override
        public Sort getSort() {
            return SystemSorts.STRING;
        }

        @Override
        public String getMCMTDeclaration() {
            return ":global " + "lifecycleEmpty " + SystemSorts.STRING + "\n";
        }
    };

    @Override
    public void setLifeCycle(int code) {
    }


}
