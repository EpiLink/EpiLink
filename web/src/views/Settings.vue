<template>
    <div id="settings">
        <div id="settings-wrapper" :class="{ 'seen': seen }">
            <div id="left">
                <link-user />
                <link-stepper step="3" />
            </div>

            <div id="right">
                <div id="options">
                    <div class="option">
                        <div><link-checkbox v-model="shareIdentity" /></div>
                        <div class="text">
                            <p class="title">Autoriser le système à conserver et utiliser mon identité (facultatif)</p>
                            <p class="description">
                                - Permet d'accéder à des ressources protégées et de recevoir des rôles (e.g. promotion)
                                automatiquement<br/>
                                - Vous servez averti de tout accès à votre identité<br/>
                                - Nous ne récupèrerons que ce qui se trouve sur le CRI, que nous ne partagerons à personne<br/>
                                - Ce paramètre peut être changé à tout moment<br/>
                            </p>
                            <p class="warning">
                                Attention : Sans cette option, les serveurs de promotion seront fortement restreints
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
                    <button id="link">Lier mon compte</button>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import LinkCheckbox from '../components/Checkbox';
    import LinkStepper  from '../components/Stepper';
    import LinkUser     from '../components/User';

    export default {
        name: 'link-settings',
        components: { LinkCheckbox, LinkUser, LinkStepper },

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
                shareIdentity: false,
                acceptConditions: false
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
        display: flex;
        flex-direction: column;
        justify-content: space-evenly;

        flex: 0.5;

        padding: 5px 35px;
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
            background-color: $primary-color;

            padding: 8px 50px;

            @include lato(500);
            font-size: 22px;

            cursor: pointer;

            transition: background-color .175s;

            &:hover {
                background-color: darken($primary-color, 3.5%);
            }
        }
    }
</style>