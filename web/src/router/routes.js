/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */
import Home       from '../views/Home.vue';
import IdProvider from '../views/IdProvider.vue';
import NotFound   from '../views/NotFound.vue';
import MetaText   from '../views/MetaText.vue';
import Profile    from '../views/Profile.vue';
import Redirect   from '../views/Redirect.vue';
import Auth       from '../views/Auth.vue';
import Settings   from '../views/Settings.vue';
import About      from '../views/About.vue';
import Success    from '../views/Success.vue';
import Instance   from '../views/Instance.vue';

export default [
    {
        path: '/',
        name: 'home',
        component: Home
    },
    {
        path: '/auth/:service',
        name: 'auth',
        component: Auth
    },
    {
        path: '/redirect/:service',
        name: 'redirect',
        component: Redirect
    },
    {
        path: '/idProvider',
        name: 'idProvider',
        component: IdProvider
    },
    {
        path: '/settings',
        name: 'settings',
        component: Settings
    },
    {
        path: '/success',
        name: 'success',
        component: Success
    },
    {
        path: '/profile',
        name: 'profile',
        component: Profile
    },
    {
        path: '/privacy',
        name: 'privacy',
        component: MetaText
    },
    {
        path: '/tos',
        name: 'tos',
        component: MetaText
    },
    {
        path: '/instance',
        name: 'instance',
        component: Instance
    },
    {
        path: '/about',
        name: 'about',
        component: About
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'not-found',
        component: NotFound
    }
];
