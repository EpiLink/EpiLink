<template>
    <div id="settings">
        <div id="settings-wrapper" :class="{ 'seen': seen }">
            <div id="left">
                <link-user />
                <link-stepper step="3" />
            </div>

            <div id="right">
                <transition name="fade" mode="out-in">
                    <div id="settings-form" v-if="!submitting" :key="0">
                        <div id="options">
                            <div class="option">
                                <div><link-checkbox v-model="saveEmail" /></div>
                                <div class="text">
                                    <p class="title">Se souvenir de mon E-Mail (facultatif)</p>
                                    <p class="description">
                                        - Vous obtiendrez des rôles automatiquement (e.g. rôle de promotion) et vous aurez accès
                                        à des ressources restreintes<br/>
                                        - Ce paramètre peut être changé à tout moment
                                    </p>
                                    <p class="warning">
                                        Attention : Sans cette option, les serveurs de promotion seront majoritairement restreints,
                                        car nous ne pourrons pas valider votre promotion
                                    </p>
                                </div>
                            </div>
                            <div class="option">
                                <div><link-checkbox v-model="acceptConditions" /></div>
                                <div class="text">
                                    <p class="title">
                                        J'accepte les <a href="#">conditions générales d'utilisation</a> et la
                                        <a href="#">politique de confidentialité</a>
                                    </p>
                                </div>
                            </div>
                        </div>
                        <div id="button-container">
                            <button id="link" :class="{ 'enabled': acceptConditions }" @click="submit">Lier mon compte</button>
                        </div>
                    </div>
                    <div id="submitting" v-if="submitting" :key="1">
                        <link-loading />
                    </div>
                </transition>
            </div>
        </div>
    </div>
</template>

<script>
    import LinkCheckbox from '../components/Checkbox';
    import LinkLoading  from '../components/Loading';
    import LinkStepper  from '../components/Stepper';
    import LinkUser     from '../components/User';

    export default {
        name: 'link-settings',
        components: { LinkLoading, LinkCheckbox, LinkUser, LinkStepper },

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
                seen: false,
                saveEmail: false,
                acceptConditions: false,
                submitting: false
            }
        },
        methods: {
            submit() {
                if (!this.acceptConditions || this.submitting) {
                    return;
                }

                this.submitting = true;

                this.$store.dispatch('register', this.saveEmail) // TODO: Handle errors
                    .then(() => this.$router.push({ name: 'profile' }));
            }
        }
    }
</script>

<style lang="scss" scoped>
    @import '../styles/vars';
    @import '../styles/fonts';

    #settings {
        display: flex;
    }

    #settings-wrapper {
        flex: 1;

        opacity: 0;
        transition: opacity .175s;

        &.seen {
            opacity: 1;
        }

        display: flex;
    }

    #left, #right {
        flex: 0.5;

        padding: 5px 35px;
    }

    #right {
        display: flex;
    }

    #left, #settings-form {
        display: flex;
        flex-direction: column;
        justify-content: space-evenly;
    }

    #settings-form, #submitting {
        flex: 1;
    }

    #options {
        display: flex;
        flex-direction: column;

        .option {
            display: flex;

            font-style: italic;

            .text {
                margin-left: 15px;

                font-size: 16px;

                a {
                    text-decoration: underline;
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

                .warning {
                    color: #C24343;

                    margin-top: 5px;
                }
            }
        }
    }

    #button-container {
        text-align: center;

        #link {
            border: none;
            border-radius: 4px;

            color: #FEFEFE;
            background-color: #c1c4cd;

            padding: 8px 50px;

            @include lato(500);
            font-size: 22px;

            transition: background-color .15s ease-in-out;

            &.enabled {
                cursor: pointer;
                background-color: $primary-color;

                &:hover {
                    background-color: darken($primary-color, 3.5%);
                }
            }
        }
    }

    #submitting {
        display: flex;
        justify-content: center;
        align-items: center;
    }
</style>