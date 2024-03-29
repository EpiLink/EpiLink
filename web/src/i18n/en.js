/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
export default {
    back: 'Back',

    // Pages
    home: {
        welcome: 'Welcome',
        discord: 'Log in with Discord'
    },
    auth: {
        redirecting: {
            title: 'Redirecting',
            description: 'Waiting for redirection to the external service...'
        },
        waiting: {
            title: 'Waiting',
            description: 'Waiting for confirmation in the separate window...'
        },
        fetching: {
            title: 'Loading',
            description: 'Retrieving information...'
        }
    },
    idProvider: {
        connect: 'Log in with {provider}'
    },
    notFound: {
        description: 'The requested page does not exist.'
    },
    redirect: {
        success: 'Successfully connected',
        failure: 'Connection denied'
    },
    settings: {
        remember: 'Remember who I am (optional)',

        iAcceptThe: 'I accept the ',
        terms: 'Terms of Services',
        andThe: 'and the',
        policy: 'Privacy Policy',

        link: 'Link my account'
    },
    success: {
        created: 'Your account has been created',
        access: 'You will get access to the Discord servers soon, please wait for a few minutes.',
        close: 'You can close this window.',
        profile: 'Go to my profile'
    },
    profile: {
        noticeUncheck: 'Unchecking this option will completely remove your identity from our servers.',
        noticeCheck: 'Checking this option will require you to log in to {provider} again.',

        admin: 'Administrator',
        identityAccesses: 'Accesses to your identity',
        automatedAccess: 'Automated access',
        manualAccess: 'Manual access',
        noAccess: 'No known accesses',

        save: 'Save'
    },
    instance: {
        poweredBy: 'Powered by',
        contactTitle: 'Contact information',
        contactDesc: 'Your instance is managed by the following people. You can contact them for any request.'
    },
    about: {
        sources: 'Original sources',
        authors: 'Authors',
        disclaimer: ['For any question, please refer to the', 'instance maintainers first.'],
        website: 'Website'
    },

    layout: {
        cancel: 'Cancel the procedure',
        logout: 'Log out',

        navigation: {
            home: 'Home',
            auth: 'Authentication',
            redirect: 'Redirecting...',
            idProvider: 'Connecting to {provider}',
            settings: 'Settings',
            success: 'Success',
            profile: 'Profile',
            tos: 'Terms of Services',
            privacy: 'Privacy',
            about: 'About',
            epilink: 'EpiLink',
            'not-found': 'Page not found'
        }
    },

    error: {
        title: 'Error',
        retry: 'Retry',
        network: 'Unable to reach the server, please check your Internet connection. The server may also be under maintenance.',
        rateLimit: 'You are being rate-limited. Please try again in a few minutes.'
    },

    popups: {
        discord: 'Connection to Discord',
        idProvider: 'Connection to {provider}'
    },
    steps: {
        discord: 'Connection to Discord',
        idProvider: 'Connection to {provider}',
        settings: 'Settings review'
    },

    meta: {
        downloadPdf: 'Download this PDF file'
    },

    // Keys received directly from the back-end
    // This is a subset of the keys you see in the documentation because it's impossible for the front-end
    // to receive some of them.
    backend: {
        ms: {
            nea: 'This account does not have an email address.'
        },
        oa: {
            iac: 'Invalid authorization code.'
        },
        use: {
            slo: 'Successfully logged out.',
            slm: 'Successfully linked {provider} account.',
            sdi: 'Successfully deleted identity.'
        },
        pc: {
            erj: 'This e-mail address was rejected. Are you sure you are using the correct {provider} account?',
            dae: 'This Discord account already exists.',
            cba: 'This {provider} account is banned (reason: {reason})',
            ala: 'This {provider} account is already linked to another account. If an administrator asks you, give them this identifier: {idpIdHashHex}.'
        },
        reg: {
            msh: 'Missing session header.',
            isv: 'Invalid service: {service}.'
        },
        err: {
            '999': 'An unknown error occurred',
            '101': 'Account creation is not allowed',
            '102': 'Invalid authorization code',
            '104': 'This account does not have any ID',
            '105': 'This service is not known or does not exist',
            '110': 'The identity of this account is already registered in the database',
            '111': 'The identity of this account already does not exist in the database',
            '112': 'This account\'s identity does not match the new one',
            '113': 'You cannot remove your identity at this time. Please wait for a few hours and try again.',
            '201': 'Something went wrong with a Discord API call',
            '202': 'Something went wrong with a {provider} API call',
            '300': 'You need authentication to be able to access this resource',
            '301': 'You do not have permission to do that.',
        }
    }
};
