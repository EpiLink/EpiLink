/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
import { createApp } from 'vue';

import router from './router';
import store  from './store';
import i18n   from './i18n';

import App from './App.vue';

const app = createApp(App);

app.use(router);
app.use(store);
app.use(i18n);

app.mount('body');
