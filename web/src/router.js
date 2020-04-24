import Vue       from 'vue';
import VueRouter from 'vue-router';

import store from './store';

import Home      from './views/Home';
import Microsoft from './views/Microsoft';
import NotFound  from './views/NotFound';
import MetaText  from './views/MetaText';
import Profile   from './views/Profile';
import Redirect  from './views/Redirect';
import Auth      from './views/Auth';
import Settings  from './views/Settings';
import About     from './views/About';
import Success   from './views/Success';

Vue.use(VueRouter);

const routes = [
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
        path: '/microsoft',
        name: 'microsoft',
        component: Microsoft
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
        path: '/about',
        name: 'about',
        component: About
    },
    {
        path: '*',
        name: 'not-found',
        component: NotFound
    }
];

const router = new VueRouter({
    mode: 'history',
    base: process.env.BASE_URL,
    routes
});

router.beforeEach((to, from, next) => {
    const path = to.fullPath;
    const fromPath = from.fullPath;

    const state = store.state;

    const go = p => next(path !== p ? p : undefined);

    const authAuthorized = (path === '/auth/discord' && fromPath === '/') || (path === '/auth/microsoft' && fromPath === '/microsoft');
    const isAuthRoute = path === '/' || path === '/microsoft' || path === '/settings';

    if (isAuthRoute || (path.startsWith('/auth/') && !authAuthorized) || (path === '/success' && fromPath !== '/settings')) {
        if (!state.user || !state.user.username) {
            return go('/');
        }

        if (!state.user.temp) {
            return go('/profile');
        }

        if (state.user.email) {
            return go('/settings');
        }

        if (state.user.username) {
            return go('/microsoft');
        }
    }

    if (path === '/profile' && (!state.user || state.user.temp)) {
        return go('/');
    }

    next();
});

export default router