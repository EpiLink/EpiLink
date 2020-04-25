<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div class="stepper">
        <div class="step" v-for="(name, $i) of steps" :class="{ 'done': step > $i + 1, 'current': ~~step === $i + 1 }">
            <div class="number">
                <template v-if="step <= $i + 1">{{ $i + 1 }}</template>
                <img v-else src="../../assets/check.svg" />
            </div>
            <div class="separator"></div>
            <div class="name" v-html="$t(`steps.${name}`)" />
        </div>
    </div>
</template>

<script>
    const STEPS = ['discord', 'microsoft', 'settings'];

    export default {
        name: 'link-stepper',
        props: ['step'],

        data() {
            return {
                steps: STEPS
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/fonts';
    @import '../styles/vars';

    .step {
        display: flex;
        align-items: center;

        margin-bottom: 15px;

        .number {
            border-radius: 50%;

            width: 35px;
            height: 35px;

            display: flex;
            justify-content: center;
            align-items: center;

            color: white;
            font-size: 21px;
        }

        .separator {
            height: 1px;
            flex: 1;

            margin: 0 25px;
        }

        .name {
            font-size: 21px;
        }

        &.done {
            .number {
                background-color: $primary-color;
            }
        }

        &:not(.done) {
            .number {
                padding-bottom: 1px;
                box-sizing: border-box;
            }
        }

        &.current {
            .separator, .number {
                background-color: #000;
            }

            .name {
                color: #000;
            }
        }

        &:not(.current) {
            .name {
                color: #A7AEC2;
            }

            .separator {
                background-color: #C2C2C2;
            }
        }

        &:not(.done):not(.current) {
            .number {
                background-color: #CACACA;
            }
        }
    }
</style>