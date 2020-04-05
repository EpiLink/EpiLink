<template>
    <div id="settings">
        <div id="settings-wrapper"  :class="{ 'seen': seen }">
            Salut je suis les paramètres
        </div>
    </div>
</template>

<script>
    export default {
        name: 'link-settings',

        mounted() {
            setTimeout(() => this.$store.commit('setExpanded', true), 300);
            setTimeout(() => this.seen = true, 700);
        },
        destroyed() {
            this.$store.commit('setExpanded', false);
        },
        data() {
            return {
                seen: false
            }
        }
    }
</script>

<style lang="scss">
    #settings-wrapper {
        opacity: 0;
        transition: opacity .175s;

        &.seen {
            opacity: 1;
        }
    }
</style>