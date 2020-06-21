<template>
    <li class="route">
        <router-link v-if="r.route" :to="{ name: r.route }" v-html="$t(`layout.navigation.${r.route}`)" />
        <a v-if="r.url" :href="r.url" target="_blank">{{ r.name }}</a>
    </li>
</template>

<script>
    export default {
        name: 'link-route',
        props: ['r']
    }
</script>