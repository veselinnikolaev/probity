package me.veselin.probity.common.util;

public final class ApiRoutes {

    private ApiRoutes() {
    }

    public static final String API = "/api";
    public static final String V1 = API + "/v1";

    public static final class Auth {
        private Auth() {
        }

        public static final String ROOT = V1 + "/auth";
        public static final String LOGIN = ROOT + "/login";
        public static final String REGISTER = ROOT + "/register";
        public static final String LOGOUT = ROOT + "/logout";
        public static final String REFRESH = ROOT + "/refresh";
        public static final String CSRF = ROOT + "/csrf";
        public static final String VERIFY = ROOT + "/verify";
        public static final String RESEND_VERIFICATION = ROOT + "/resend-verification";
    }

    public static final class Users {
        private Users() {}

        public static final String ME            = V1 + "/users/me";
        public static final String PASSWORD      = ME + "/password";
        public static final String PREFERENCES   = ME + "/preferences";
        public static final String SESSIONS      = ME + "/sessions";
        public static final String SESSION       = SESSIONS + "/{sessionId}";
    }

    public static final class Portfolios {
        private Portfolios() {
        }

        public static final String PORTFOLIOS = V1 + "/portfolios";
        public static final String PORTFOLIO = PORTFOLIOS + "/{id}";
        public static final String VOLATILITY = PORTFOLIO + "/volatility";
        public static final String ALERTS = PORTFOLIO + "/alerts";
        public static final String COMPOSITION = PORTFOLIO + "/composition";
        public static final String SUMMARY = PORTFOLIO + "/summary";
        public static final String POSITIONS = PORTFOLIO + "/positions";
        public static final String RISK_METRICS = PORTFOLIO + "/risk-metrics";
        public static final String CORRELATION = PORTFOLIO + "/correlation";
        public static final String POSITION = PORTFOLIO + "/positions/{positionId}";
        public static final String VAR = PORTFOLIO + "/var";
    }

    public static final class Assets {
        private Assets() {
        }

        public static final String ASSETS = V1 + "/assets";
        public static final String SEARCH = ASSETS + "/search";
    }

    public static final class Simulations {
        private Simulations() {
        }

        public static final String SIMULATIONS = V1 + "/simulations";
        public static final String RUN = SIMULATIONS + "/run";
        public static final String RUN_ASYNC = SIMULATIONS + "/run-async";
        public static final String SIMULATION = SIMULATIONS + "/{id}";
        public static final String STATUS = SIMULATIONS + "/{id}/status";
    }

    public static final class Assistant {
        public static final String ASSISTANT = V1 + "/assistant";
        public static final String CHAT = ASSISTANT + "/chat";
    }
}
