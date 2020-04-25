import Home      from '../views/Home';
import Microsoft from '../views/Microsoft';
import NotFound  from '../views/NotFound';
import MetaText  from '../views/MetaText';
import Profile   from '../views/Profile';
import Redirect  from '../views/Redirect';
import Auth      from '../views/Auth';
import Settings  from '../views/Settings';
import About     from '../views/About';
import Success   from '../views/Success';

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