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
        redirecting: {
            title: 'Redirection',
            description: 'Redirection vers le service en cours...'
        },
        waiting: {
            title: 'En attente',
            description: 'En attente de la confirmation dans la fenêtre séparée...'
        },
        fetching: {
            title: 'Chargement',
            description: 'Récupération des informations...'
        }
    },
    idProvider: {
        connect: 'Se connecter via {provider}'
    },
    notFound: {
        description: "La page demandée n'existe pas."
    },
    redirect: {
        success: 'Connexion réussie',
        failure: 'Connexion refusée'
    },
    settings: {
        remember: 'Se souvenir de qui je suis (facultatif)',

        iAcceptThe: "J'accepte les",
        terms: "conditions générales d'utilisation",
        andThe: 'et la',
        policy: 'politique de confidentialité',

        link: 'Lier mon compte'
    },
    success: {
        created: 'Votre compte a été créé',
        access: "Vous recevrez l'accès au(x) serveur(s) Discord sous peu, merci de patienter quelques minutes.",
        close: 'Vous pouvez fermer cette fenêtre.',
        profile: 'Voir mon profil'
    },
    profile: {
        noticeUncheck: 'Décocher cette option supprimera complètement votre identité du serveur.',
        noticeCheck: 'Après avoir coché cette option, vous devrez vous connecter à nouveau à {provider}.',

        admin: 'Administrateur',
        identityAccesses: 'Accès à votre identité',
        automatedAccess: 'Accès automatique',
        manualAccess: 'Accès manuel',
        noAccess: "Pas d'accès enregistré",

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
        disclaimer: ["Pour toute question, merci de vous adresser en priorité aux mainteneurs de l'instance", '.'],
        website: 'Site web'
    },

    layout: {
        cancel: 'Annuler la procédure',
        logout: 'Se déconnecter',

        navigation: {
            home: 'Accueil',
            auth: 'Connexion',
            redirect: 'Redirection...',
            idProvider: 'Connexion à {provider}',
            settings: 'Paramètres',
            success: 'Terminé',
            profile: 'Profil',
            epilink: 'EpiLink',
            tos: "Conditions d'utilisation",
            privacy: 'Confidentialité',
            instance: 'Instance',
            about: 'À Propos',
            'not-found': 'Page introuvable'
        }
    },

    error: {
        title: 'Erreur',
        retry: 'Réessayer',
        network: 'Impossible de contacter le serveur, veuillez vérifier votre connexion. Le serveur est peut-être en maintenance.',
        rateLimit: 'Vous avez été bloqué par le filtre anti-spam, veuillez réessayer dans quelques minutes.'
    },

    popups: {
        discord: 'Connexion à Discord',
        idProvider: 'Connexion à {provider}'
    },
    steps: {
        discord: 'Connexion à Discord',
        idProvider: 'Connexion à {provider}',
        settings: 'Validation des paramètres'
    },

    meta: {
        downloadPdf: 'Télécharger ce fichier PDF'
    },

    backend: {
        ms: {
            nea: "Ce compte ne possède pas d'adresse e-mail"
        },
        oa: {
            iac: "Code d'autorisation invalide"
        },
        use: {
            slo: 'Connecté avec succès.',
            slm: 'Compte {provider} relié avec succès.',
            sdi: 'Identité supprimée avec succès.'
        },
        pc: {
            erj: "Cette adresse e-mail a été rejetée. Êtes-vous sûr d'utiliser le bon compte {provider} ?",
            dae: 'Ce compte Discord existe déjà.',
            cba: 'Ce compte {provider} est banni (raison : {reason})',
            ala: 'Ce compte {provider} est déjà lié à un autre compte. Si un administrateur vous le demande, donnez lui cet identifiant: {idpIdHashHex}.'
        },
        reg: {
            msh: 'En-tête de session manquant.',
            isv: 'Service invalide : {service}.'
        },
        err: {
            '999': 'Une erreur inconnue est survenue.',
            '101': "La création de compte n'est pas permise",
            '102': "Code d'autorisation invalide",
            '104': "Ce compte n'a pas d'identifiant",
            '105': "Ce service est inconnu ou n'existe pas",
            '110': "L'identité de ce compte est déjà connue dans la base de données.",
            '111': "L'identité de ce compte est déjà absente de la base de données.",
            '112': "L'identifiant du compte ne correspond pas au nouveau compte.",
            '113': "Vous ne pouvez pas retirer votre identité pour le moment. Merci de patienter quelques heures et de réessayer.",
            '201': "Une erreur est survenue lors d'un appel à l'API de Discord",
            '202': "Une erreur est survenue lors d'un appel à l'API de {provider}",
            '300': "Vous avez besoin d'être connecté pour accéder à cette ressource",
            '301': "Vous n'avez pas la permission de faire cela",
        }
    }
};
