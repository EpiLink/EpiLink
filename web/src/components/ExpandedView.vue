<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div class="expanded-view">
        <div class="expanded-wrapper" :class="{ column, hidden }">
            <slot></slot>
        </div>
    </div>
</template>

<script>
    export default {
        name: 'link-expanded-view',
        props: ['column'],

        mounted() {
            setTimeout(() => this.$store.commit('setExpanded', true), 300);
        },
        destroyed() {
            this.hidden = true;
            setTimeout(() => this.$store.commit('setExpanded', false), 200);
        },

        data() {
            return {
                hidden: false
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/vars';

    .expanded-view {
        display: flex;
    }

    .expanded-wrapper {
        animation: fade .175s .7s ease 1 both;

        display: flex;
        flex: 1;

        &.column {
            flex-direction: column;
            align-items: center;
        }

        &.hidden {
            animation-direction: reverse;
        }
    }

    @media screen and (max-width: $expanded-breakpoint) {
        .expanded-wrapper {
            animation-delay: 0s;

            flex-direction: column;
            overflow-y: auto;
        }
    }
</style>