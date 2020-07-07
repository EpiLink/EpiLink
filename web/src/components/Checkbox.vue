<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div class="checkbox" :class="{ 'ticked': checked }" @click="tick">
        <img class="tick" alt="Checkbox" src="../../assets/check.svg" />
    </div>
</template>

<script>
    export default {
        name: 'link-checkbox',
        props: ['value'],

        data() {
            return {
                checked: this.value
            }
        },
        methods: {
            tick() {
                this.checked = !this.checked;
                this.$emit('input', this.checked);
            }
        },
        watch: {
            value(val) {
                this.checked = val;
            }
        }
    }
</script>

<style lang="scss">
    @import '../styles/vars';

    $unchecked-color: #D7DFE7;

    .checkbox {
        display: inline-flex;
        justify-content: center;
        align-items: center;

        border-radius: 3px;

        background-color: $unchecked-color;
        transition: background-color .175s;

        padding: 1px;

        cursor: pointer;

        &.ticked {
            background-color: $primary-color;

            &:hover {
                background-color: darken($primary-color, 3.5%);
            }
        }

        &:not(.ticked) {
            &:hover {
                background-color: darken($unchecked-color, 4%);
            }

            .tick {
                opacity: 0;
            }
        }
    }

    .tick {
        width: 26px;
        height: 26px;

        transition: opacity .125s;
    }
</style>