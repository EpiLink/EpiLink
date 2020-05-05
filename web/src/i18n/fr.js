/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
export default {
    back: 'Retour',

    // Pages
    home: {
        welcome: 'Bienvenue',
        discord: 'Se connecter via Discord'
    },
    auth: {
        waiting: {
            title: 'En attente',
            description: 'En attente de la confirmation dans la fenêtre séparée...'
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
        description: 'La page demandée n\'existe pas.'
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
    success: {
        created: 'Votre compte a été créé',
        access: 'Vous recevrez l\'accès au(x) serveur(s) Discord sous peu, merci de patienter quelques minutes.',
        close: 'Vous pouvez fermer cette fenêtre.',
        profile: 'Voir mon profil'
    },
    profile: {
        noticeUncheck: 'Décocher cette option supprimera complètement votre identité du serveur.',
        noticeCheck: 'Après avoir coché cette option, vous devrez vous connecter à nouveau à Microsoft.',

        admin: 'Administrateur',
        identityAccesses: 'Accès à votre identité',
        automatedAccess: 'Accès automatique',
        manualAccess: 'Accès manuel',
        noAccess: 'Pas d\'accès enregistré',

        save: 'Sauvegarder'
    },
    instance: {
        poweredBy: 'Propulsé par',
        contactTitle: 'Informations de contact',
        contactDesc: 'Cette instance est gérée par les personnes suivantes. Vous pouvez les contacter pour toute requête.'
    },
    about: {
        sources: 'Sources originales',
        authors: 'Auteurs',
        disclaimer: ['Pour toute question, merci de vous adresser en priorité aux mainteneurs de l\'instance', '.']
    },

    layout: {
        cancel: 'Annuler la procédure',
        logout: 'Se déconnecter',

        navigation: {
            home: 'Accueil',
            tos: 'Conditions d\'utilisation',
            privacy: 'Confidentialité',
            instance: 'Instance',
            about: 'À Propos'
        }
    },

    error: {
        title: 'Erreur',
        retry: 'Rééssayer'
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
};
