<template>
    <div class="checkbox" :class="{ 'ticked': checked }" @click="tick">
        <img class="tick" src="../../assets/check.svg" />
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