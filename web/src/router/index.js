import Vue       from 'vue';
import VueRouter from 'vue-router';

import before from './before';
import routes from './routes';

Vue.use(VueRouter);

const router = new VueRouter({
    mode: 'history',
    base: process.env.BASE_URL,
    routes
});

router.beforeEach(before);

export default router;