import Vue  from 'vue';
import Vuex from 'vuex';

import request, { deleteSession, isPermanentSession } from './api';

Vue.use(Vuex);

export default new Vuex.Store({
    state: {
        expanded: false,
        meta: null,
        popup: null,
        user: null,
        privacyPolicy: null,
        termsOfService: null
    },
    mutations: {
        setExpanded(state, expanded) {
            state.expanded = expanded;
        },
        setMeta(state, meta) {
            if (!state.meta) {
                state.meta = meta;
                window.document.title = meta.title;
            }
        },
        openPopup(state, popup) {
            if (state.popup) {
                state.popup.close();
            }

            state.popup = popup;
        },
        closePopup(state) {
            if (!state.popup) {
                return;
            }

            state.popup.close();
            state.popup = null;
        },
        setTempProfile(state, { discordUsername, discordAvatarUrl, email }) {
            console.log(`Logged in as '${discordUsername}' (temporary session)`);

            state.user = {
                temp: true,

                username: discordUsername,
                avatar: discordAvatarUrl,
                email
            };
        },
        setProfile(state, { username, avatar }) {
            console.log(`Logged in as '${username}' (permanent session)`);

            state.user = { temp: false, username, avatar };
        },
        setRegistered(state) {
            if (!state.user.temp) {
                return;
            }

            state.user.temp = false;
        },
        logout(state) {
            state.user = null;
            deleteSession();
        },
        setPrivacyPolicy(state, policy) {
            if (!state.privacyPolicy) {
                state.privacyPolicy = policy;
            }
        },
        setTermsOfService(state, terms) {
            if (!state.termsOfService) {
                state.termsOfService = terms;
            }
        }
    },
    actions: {
        async load({ state, commit }) {
            if (state.meta) {
                return;
            }

            if (isPermanentSession()) {
                let user;
                try {
                    user = await request('/user');
                } catch(e) {
                    console.warn('Could not retrieve logged user, session probably expired');
                    console.warn(e);

                    commit('logout');
                }

                if (user && user.username) {
                    commit('setProfile', user);
                }
            } else {
                const user = await request('/register/info');

                if (user.discordUsername) {
                    commit('setTempProfile', user);
                }
            }

            commit('setMeta', await request('/meta/info'));
        },
        async postCode({ state, commit }, { service, code, uri }) {
            const { next, attachment } = await request('POST', '/register/authcode/' + service, {
                code,
                redirectUri: uri
            });

            if (next === 'continue') {
                commit('setTempProfile', attachment);
            } else if (next === 'login') {
                commit('setProfile', await request('/user'));
            } else {
                console.log(`Unknown next step ${next}`);
            }
        },
        async logout({ state, commit }) {
            if (!state.user) {
                return;
            }

            if (state.user.temp) {
                await request('DELETE', '/register');
            } else {
                await request('POST', '/user/logout');
            }

            console.log('Successfully logged out');
            commit('logout');
        },
        async register({ state, commit }, saveEmail) {
            if (!state.user || !state.user.temp) {
                return;
            }

            await request('POST', '/register', { keepIdentity: saveEmail });

            commit('setRegistered');
        },
        async fetchPrivacyPolicy({ state, commit }) {
            if (state.privacyPolicy) {
                return;
            }

            commit('setPrivacyPolicy', await request('/meta/privacy'));
        },
        async fetchTermsOfService({ state, commit }) {
            if (state.termsOfService) {
                return;
            }

            commit('setTermsOfService', await request('/meta/tos'));
        }
    }
});
