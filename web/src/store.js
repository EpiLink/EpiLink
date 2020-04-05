import Vue  from 'vue';
import Vuex from 'vuex';

import request, { deleteSession } from './api';

Vue.use(Vuex);

export default new Vuex.Store({
    state: {
        expanded: false,
        meta: null,
        popup: null,
        user: null
    },
    mutations: {
        setExpanded(state, expanded) {
            state.expanded = expanded;
        },
        setMeta(state, meta) {
            if (!state.meta) {
                state.meta = meta;
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
        setProfile(state, { discordUsername, discordAvatarUrl, email, temp }) {
            console.log(`Logged in as '${discordUsername}'`);
            state.user = {
                username: discordUsername,
                avatar: discordAvatarUrl,
                email,
                temp
            };
        },
        logout(state) {
            state.user = null;
            deleteSession();
        }
    },
    actions: {
        async load({ state, commit }) {
            if (state.meta) {
                return;
            }

            commit('setMeta', await request('/meta/info'));
            const user = await request('/register/info');
            if (user.discordUsername) {
                commit('setProfile', { temp: true, ...user });
            }
        },
        async postCode({ state, commit }, { service, code, uri }) {
            const { next, attachment } = await request('POST', '/register/authcode/' + service, {
                code,
                redirectUri: uri
            });

            if (next === 'continue') {
                commit('setProfile', { temp: true, ...attachment });
            } else if (next === 'login') {
                // TODO: This
            } else {
                console.log(`Unknown next step ${next}`);
            }
        }
    }
});
