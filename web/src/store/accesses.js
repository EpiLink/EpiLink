/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
import request from '../api';

export default {
    state: {
        shouldDisclose: null,
        accesses: null,
    },
    mutations: {
        setAccesses(state, { manualAuthorsDisclosed, accesses }) {
            state.shouldDisclose = manualAuthorsDisclosed;
            state.accesses = accesses;
        }
    },
    actions: {
        async fetchAccesses({ commit }) {
            commit('setAccesses', await request('/user/idaccesslogs'));
        }
    }
};