<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
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
    @import 'src/styles/mixins';

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

    @media screen and (max-width: 450px) {
        .link {
            font-size: 18px;
            padding: 8px 25px;
        }
    }

    @media screen and (max-width: 355px) {
        .link {
            font-size: 16px;
            padding: 8px 10px;
        }
    }
</style>