import Vue from 'vue';
import VueI18n from 'vue-i18n';

Vue.use(VueI18n);

const messages = {
    en: {
        // Pages
        home: {
            welcome: 'Welcome',
            discord: 'Log in with Discord'
        },
        auth: {
            waiting: {
                title: 'Waiting',
                description: 'Waiting for confirmation in the separate window...'
            },
            fetching: {
                title: 'Loading',
                description: 'Retrieving information...'
            }
        },
        microsoft: {
            connect: 'Log in with Microsoft'
        },
        notFound: {
            description: 'The requested page does not exist'
        },
        redirect: {
            success: 'Successfully connected',
            failure: 'Connection refused'
        },
        settings: {
            remember: 'Remember who I am (optional)',

            iAcceptThe: 'I accept the ',
            terms: 'Terms of Services',
            andThe: 'and the',
            policy: 'Privacy Policy',

            link: 'Link my account'
        },

        layout: {
            cancel: 'Cancel the procedure',
            logout: 'Log out',

            navigation: {
                instance: 'Instance',
                privacy: 'Privacy',
                sources: 'Sources',
                about: 'About'
            }
        },

        popups: {
            discord: 'Connection to Discord',
            microsoft: 'Connection to Microsoft'
        },
        steps: {
            discord: 'Connection to Discord',
            microsoft: 'Connection to Microsoft',
            settings: 'Settings review'
        }
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
            success: 'Connexion réussie',
            failure: 'Connexion refusée'
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