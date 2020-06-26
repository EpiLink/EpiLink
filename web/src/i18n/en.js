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
    microsoft: {
        connect: 'Log in with Microsoft'
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
        noticeCheck: 'Checking this option will require you to log in to Microsoft again.',

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
        disclaimer: ['For any question, please refer to the', 'instance maintainers first.']
    },

    layout: {
        cancel: 'Cancel the procedure',
        logout: 'Log out',

        navigation: {
            home: 'Home',
            tos: 'Terms of Services',
            privacy: 'Privacy',
            instance: 'Instance',
            about: 'About'
        }
    },

    error: {
        title: 'Error',
        retry: 'Retry'
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
};
