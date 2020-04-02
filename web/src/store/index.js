import Vue  from 'vue';
import Vuex from 'vuex';

import request from '../api';

Vue.use(Vuex);

export default new Vuex.Store({
    state: {
        meta: null
    },
    mutations: {
        setMeta(state, meta) {
            if (!state.meta) {
                state.meta = meta;
            }
        }
    },
    actions: {
        async fetchMeta({ state, commit }) {
            if (state.meta) {
                return;
            }

            commit('setMeta', await request('/meta/info'));
        }
    }
});
