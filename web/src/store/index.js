/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
import Vue  from 'vue';
import Vuex from 'vuex';

import auth from './auth';
import texts from './texts';
import accesses from './accesses';

import request from '../api';

Vue.use(Vuex);

export default new Vuex.Store({
    modules: { auth, texts, accesses },

    state: {
        expanded: false,
        popup: null,
        meta: null
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
        }
    },
    actions: {
        async load({ state, commit, dispatch }) {
            if (state.meta) {
                return;
            }

            await dispatch('refresh');
            commit('setMeta', await request('/meta/info'));
        }
    }
});
