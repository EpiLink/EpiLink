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