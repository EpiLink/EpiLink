import request from '../api';

export default {
    state: {
        termsOfService: null,
        privacyPolicy: null
    },
    mutations: {
        setTermsOfService(state, terms) {
            if (!state.termsOfService) {
                state.termsOfService = terms;
            }
        },
        setPrivacyPolicy(state, policy) {
            if (!state.privacyPolicy) {
                state.privacyPolicy = policy;
            }
        }
    },
    actions: {
        async fetchTermsOfService({ state, commit }) {
            if (state.termsOfService) {
                return;
            }

            commit('setTermsOfService', await request('/meta/tos'));
        },

        async fetchPrivacyPolicy({ state, commit }) {
            if (state.privacyPolicy) {
                return;
            }

            commit('setPrivacyPolicy', await request('/meta/privacy'));
        }
    }
};