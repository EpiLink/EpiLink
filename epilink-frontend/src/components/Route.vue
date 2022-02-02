<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <li class="route">
        <router-link v-if="r.route" :to="{ name: r.route }" v-html="$t(`layout.navigation.${r.route}`, {provider: providerName})" />
        <a v-if="r.url" :href="r.url" rel="noreferrer" target="_blank">{{ r.name }}</a>
    </li>
</template>

<script>
    export default {
        name: 'link-route',
        props: ['r'],
        computed: {
            providerName() {
                const meta = this.$store.meta;
                return meta && meta.providerName;
            }
        }
    };
</script>
