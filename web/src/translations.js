import Vue from 'vue';
import VueI18n from 'vue-i18n';

Vue.use(VueI18n)

const messages = {
    en: {
        // ...
    },
    fr: {
        // Pages
        home: {
            welcome: 'Bienvenue',
            discord: 'Se connecter via Discord'
        },
        auth: {
            waiting: {
                title: 'En attente',
                description: 'En attente de la confirmation dans la fenêtre séparée'
            },
            fetching: {
                title: 'Chargement',
                description: 'Récupération des informations...'
            }
        },
        microsoft: {
            connect: 'Se connecter via Microsoft'
        },
        notFound: {
            description: 'La page demandée n\'existe pas'
        },
        redirect: {
            success: 'Connexion réussie'
        },
        settings: {
            remember: 'Se souvenir de qui je suis (facultatif)',

            iAcceptThe: 'J\'accepte les',
            terms: 'conditions générales d\'utilisation',
            andThe: 'et la',
            policy: 'politique de confidentialité',

            link: 'Lier mon compte'
        },

        layout: {
            cancel: 'Annuler la procédure',
            logout: 'Se déconnecter',

            navigation: {
                instance: 'Instance',
                privacy: 'Confidentialité',
                sources: 'Sources',
                about: 'À Propos'
            }
        },

        popups: {
            discord: 'Connexion à Discord',
            microsoft: 'Connexion à Microsoft'
        },
        steps: {
            discord: 'Connexion à Discord',
            microsoft: 'Connexion à Microsoft',
            settings: 'Validation des paramètres'
        }
    }
};

export default new VueI18n({
    locale: window.navigator.language.slice(0, 2) === 'fr' ? 'fr' : 'en',
    messages: messages
});