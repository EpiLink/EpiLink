<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div class="loading">
        <div v-for="_ of new Array(13)" class="loading-tick">
        </div>
    </div>
</template>

<script>
    export default {
        name: 'link-loading'
    }
</script>

<style lang="scss" scoped>
    .loading {
        position: relative;

        width: 70px;
        height: 70px;
    }

    .loading-tick {
        transform-origin: 32px 32px;
        animation: 1.2s loader-fade ease-in-out infinite both;

        &::after {
            content: "";

            display: block;

            position: absolute;
            top: 0;
            left: 30px;

            width: 5px;
            height: 18px;

            border-radius: 10px;

            background-color: #1b1f22;
        }

        @for $i from 1 through 13 {
            &:nth-child(#{$i}) {
                transform: rotate(30deg * ($i - 1));
                animation-delay: ($i - 1) * 0.1s;
            }
        }
    }

    @keyframes loader-fade {
        0% {
            opacity: 0.1;
        }
        40% {
            opacity: 1;
        }
        80% {
            opacity: 0.1;
        }
        100% {
            opacity: 0.1;
        }
    }
</style>