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

            const req = await request('GET', '/meta/tos', null, true);
            const isPdf = req.headers.get('Content-Type') === 'application/pdf';
            commit('setTermsOfService', {'textContent': isPdf ? null : await req.text(), 'pdfContent': isPdf ? URL.createObjectURL(await req.blob()) : null, 'url': req.url});
        },

        async fetchPrivacyPolicy({ state, commit }) {
            if (state.privacyPolicy) {
                return;
            }

            const req = await request('GET', '/meta/privacy', null, true);
            const isPdf = req.headers.get('Content-Type') === 'application/pdf';
            commit('setPrivacyPolicy', {'textContent': isPdf ? null : await req.text(), 'pdfContent': isPdf ? URL.createObjectURL(await req.blob()) : null, 'url': req.url});
        }
    }
};