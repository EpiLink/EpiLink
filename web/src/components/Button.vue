<template>
    <div class="button-container">
        <a class="link" @click="action" :class="{ enabled }">
            <slot></slot>
        </a>
    </div>
</template>

<script>
    export default {
        name: 'link-button',
        props: ['enabled'],

        methods: {
            action() {
                this.enabled && this.$emit('action');
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/vars';
    @import '../styles/fonts';

    .button-container {
        text-align: center;
    }

    .link {
        border: none;
        border-radius: 4px;

        color: #FEFEFE;
        background-color: #C3C6CD;

        padding: 8px 50px;

        @include lato(500);
        font-size: 22px;

        transition: background-color .15s ease-in-out;

        cursor: default;

        &.enabled {
            cursor: pointer;
            background-color: $primary-color;

            &:hover {
                background-color: darken($primary-color, 3.5%);
            }
        }
    }
</style>