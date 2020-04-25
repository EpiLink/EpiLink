/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
import Vue     from 'vue';
import VueI18n from 'vue-i18n';

import en from './en';
import fr from './fr';

Vue.use(VueI18n);

export default new VueI18n({
    locale: window.navigator.language.slice(0, 2) === 'fr' ? 'fr' : 'en',
    messages: { en, fr }
});
