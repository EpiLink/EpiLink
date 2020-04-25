/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
import request, { deleteSession, isPermanentSession } from '../api';

export default {
    state: {
        user: null
    },
    mutations: {
        setTempProfile(state, { discordUsername, discordAvatarUrl, email }) {
            console.log(`Logged in as '${discordUsername}' (temporary session)`);

            state.user = {
                temp: true,

                username: discordUsername,
                avatar: discordAvatarUrl,
                email
            };
        },
        setProfile(state, { username, avatarUrl, identifiable }) {
            console.log(`Logged in as '${username}' (permanent session)`);

            state.user = {
                temp: false,

                username,
                avatar: avatarUrl,
                identifiable
            };
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
        setIdentifiable(state, identifiable) {
            if (!state.user) {
                return;
            }

            state.user.identifiable = identifiable;
        }
    },
    actions: {
        async refresh({ commit }) {
            if (!isPermanentSession()) {
                const user = await request('/register/info');

                if (user.discordUsername) {
                    commit('setTempProfile', user);
                }

                return;
            }

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

        async register({ state, commit }, saveEmail) {
            if (!state.user || !state.user.temp) {
                return;
            }

            await request('POST', '/register', { keepIdentity: saveEmail });

            commit('setRegistered');
            commit('setIdentifiable', saveEmail);
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

        async removeIdentity({ state, commit }) {
            if (!state.user || !state.user.identifiable) {
                return;
            }

            await request('DELETE', '/user/identity');
            commit('setIdentifiable', false);
        },

        async postIdentity({ state, commit }, { code, uri }) {
            if (!state.user || state.user.identifiable) {
                return;
            }

            await request('POST', '/user/identity', {
                code,
                redirectUri: uri
            });

            commit('setIdentifiable', true);
        }
    }
};