package it.unibz.deltabpmn.dataschema.core;

import it.unibz.deltabpmn.dataschema.elements.Constant;
import it.unibz.deltabpmn.dataschema.elements.Sort;

/**
 * Stores constants representing boolean values {@code TRUE} and {@code FALSE}.
 * Also stores a special constant {@code NULL} to denote an undefined value in any domain of any sort.
 */
public enum SystemConstants implements Constant {

    TRUE {
        @Override
        public String getName() {
            return "True";
        }

        @Override
        public Sort getSort() {
            return SystemSorts.BOOL;
        }
    },

    FALSE {
        @Override
        public String getName() {
            return "False";
        }

        @Override
        public Sort getSort() {
            return SystemSorts.BOOL;
        }
    },

    NULL {

        @Override
//        /**
//         * This method is not supported for that system constant.
//         * @return
//         */
        public String getName() {
//            try {
//                throw new UnsupportedOperationException();
//            } catch (UnsupportedOperationException ex) {
//                System.out.println("Method not supported");
//            }
            return "NULL";
        }

        @Override
//        /**
//         * This method is not supported for that system constant.
//         * @return
//         */
        public Sort getSort() {
//            try {
//                throw new UnsupportedOperationException();
//            } catch (UnsupportedOperationException ex) {
//                System.out.println("Method not supported");
//            }
            return SystemSorts.STRING;
        }
    };


    @Override
    public String getMCMTDeclaration() {
        return ":smt (define " + getName() + " ::" + getSort().getSortName() + ")\n";
    }
}
