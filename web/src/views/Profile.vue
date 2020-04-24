<template>
    <div id="profile">
        <div id="profile-wrapper" :class="{ seen }">
            Salut à toi {{ user.username }}
        </div>
    </div>
</template>

<script>
    import { mapState } from 'vuex';

    export default {
        name: 'link-profile',
        computed: mapState(['user']),

        mounted() {
            setTimeout(() => this.$store.commit('setExpanded', true), 300);
            setTimeout(() => this.seen = true, 700);
        },
        destroyed() {
            this.seen = false;
            setTimeout(() => this.$store.commit('setExpanded', false), 200);
        },
        data() {
            return {
                seen: false
            }
        }
    }
</script>

<style lang="scss" scoped>
    #profile {
        display: flex;
    }

    #profile-wrapper {
        flex: 1;

        opacity: 0;
        transition: opacity .175s;

        &.seen {
            opacity: 1;
        }
    }
</style>