<template>
    <div class="option">
        <div><link-checkbox v-model="data" /></div>
        <div class="text">
            <slot></slot>
        </div>
    </div>
</template>

<script>
    import LinkCheckbox from './Checkbox';

    export default {
        name: 'link-option',
        props: ['value'],
        components: { LinkCheckbox },

        data() {
            return {
                data: this.value
            }
        },
        watch: {
            value(val) {
                this.data = val;
            },
            data(val) {
                this.$emit('input', val);
            }
        }
    }
</script>

<style lang="scss">
    .option {
        display: flex;

        font-style: italic;
    }

    .text {
        margin-left: 15px;

        font-size: 16px;

        a {
            text-decoration: underline;
        }

        .id-prompt {
            display: contents;
        }

        .title {
            margin-top: 0;
            margin-bottom: 0;

            font-size: 20px;
        }

        .description {
            color: #70777F;

            margin-left: 5px;
            margin-top: 10px;
            margin-bottom: 0;
        }

        .warning, .notice {
            margin: 5px 0;
        }

        .warning {
            color: #C24343;
        }

        .notice {
            color: #57BD57;
        }
    }
</style>