<template>
    <div>
        Salut à toi {{ user.username }}
    </div>
</template>

<script>
    import { mapState } from 'vuex';

    export default {
        name: 'link-profile',
        computed: mapState(['user'])
    }
</script>