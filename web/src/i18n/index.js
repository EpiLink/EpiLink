import Vue     from 'vue';
import VueI18n from 'vue-i18n';

import en from './en';
import fr from './fr';

Vue.use(VueI18n);

export default new VueI18n({
    locale: window.navigator.language.slice(0, 2) === 'fr' ? 'fr' : 'en',
    messages: { en, fr }
});
