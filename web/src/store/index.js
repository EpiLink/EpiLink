/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
import { createStore } from 'vuex';

import auth     from './auth';
import texts    from './texts';
import accesses from './accesses';

import request   from '../api';
import { toRaw } from 'vue';

export default createStore({
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
                // Change the favicon to the instance logo
                // We can't bind the favicon in a nice vueish way because it is outside of Vue's virtual DOM (it's
                // directly in our index.html)
                if (meta.logo != null) {
                    document.querySelector('[rel=icon]').href = meta.logo;
                }
            }
        },
        openPopup(state, popup) {
            state = toRaw(state);

            if (state.popup) {
                state.popup.close();
            }

            state.popup = popup;
        },
        closePopup(state) {
            state = toRaw(state);

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
