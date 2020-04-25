/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
import Vue from 'vue';

import router from './router';
import store  from './store';
import i18n   from './i18n';

import App from './App.vue';

Vue.config.productionTip = false;

new Vue({
    router,
    store,
    i18n,

    render: h => h(App)
}).$mount('#app');
