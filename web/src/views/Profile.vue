<template>
    <div id="profile">
        <div id="profile-wrapper" :class="{ seen }">
            <div id="left">
                <link-user />

                <h2 id="accesses-title" v-html="$t('profile.identityAccesses')" />
                <div id="accesses-container">
                    <transition name="fade" mode="out-in">
                        <div id="accesses" v-if="accesses" :key="0">
                            <div class="access" v-for="(access, i) of accesses" :class="{ first: !i }" :key="i">
                                <div>
                                    <span class="author">{{ access.author }}</span>
                                    -
                                    <span class="type" v-html="$t(`profile.${access.automated ? 'automated' : 'manual'}Access`)" />
                                </div>
                                <span class="date">- {{ access.timestamp | date }}</span>
                                <p class="reason">{{ access.reason }}</p>
                            </div>
                        </div>
                        <div id="loading-container" v-if="!accesses" :key="1">
                            <link-loading />
                        </div>
                    </transition>
                </div>
            </div>
            <div id="right">
                <link-option v-model="saveEmail">
                    <p class="title" v-html="$t('settings.remember')" />
                    <div class="id-prompt" v-html="meta.idPrompt" />
                    <p class="notice">Note : {{ $t('profile.notice' + (wasChecked ? 'Uncheck' : 'Check')) }}</p>
                </link-option>

                <link-button :enabled="saveEmail !== wasChecked" @action="submit">
                    {{ saveEmail && !wasChecked ? $t('microsoft.connect') : $t('profile.save') }}
                </link-button>
            </div>
        </div>
    </div>
</template>

<script>
    import { mapState } from 'vuex';
    import LinkButton   from '../components/Button';
    import LinkLoading  from '../components/Loading';

    import LinkOption from '../components/Option';
    import LinkUser   from '../components/User';

    export default {
        name: 'link-profile',
        components: { LinkLoading, LinkButton, LinkOption, LinkUser },

        mounted() {
            setTimeout(() => this.$store.commit('setExpanded', true), 300);
            setTimeout(() => this.seen = true, 700);

            this.saveEmail = this.wasChecked = this.user.identifiable;

            this.$store.dispatch('fetchAccesses'); // TODO: Handle error
        },
        destroyed() {
            this.seen = false;
            setTimeout(() => this.$store.commit('setExpanded', false), 200);
        },

        data() {
            return {
                seen: false,

                wasChecked: false,
                saveEmail: false
            }
        },
        computed: mapState({
            user: state => state.auth.user,
            meta: state => state.meta,
            accesses: state => state.accesses.accesses
        }),
        methods: {
            submit() {

            }
        },
        filters: {
            date(str) {
                const d = new Date(str);
                return d.toLocaleDateString(navigator.language, { day: 'numeric', month: 'long', year: 'numeric' });
            }
        }
    }
</script>

<style lang="scss" scoped>
    #profile {
        display: flex;
    }

    #profile-wrapper {
        flex: 1;

        display: flex;

        opacity: 0;
        transition: opacity .175s;

        &.seen {
            opacity: 1;
        }
    }

    #left, #right {
        flex: 0.5;

        display: flex;
        flex-direction: column;

        padding: 30px;
        box-sizing: border-box;
    }

    #loading-container {
        flex: 1;

        display: flex;
        justify-content: center;
        align-items: center;
    }

    #accesses-title {
        margin: 0;
        margin-top: 20px;

        text-align: center;
        font-size: 20px;
    }

    #accesses-container {
        display: flex;
        flex: 1;

        margin: 10px;
        margin-top: 15px;

        border-radius: 5px;

        background-color: #E5EAEC;

        overflow-y: auto;

        #accesses {
            display: flex;
            flex-direction: column;

            .access {
                padding: 10px;

                &:not(.first) {
                    border-top: solid 1px #222222;
                }

                .author {
                    font-weight: bold;
                    font-size: 18px;
                }

                .type, .date {
                    font-style: italic;
                }

                .date {
                    margin-left: 15px;
                }

                .reason {
                    margin: 0;
                    margin-top: 10px;

                    display: -webkit-box;
                    -webkit-box-orient: vertical;
                    -webkit-line-clamp: 3;

                    text-overflow: ellipsis;
                    overflow: hidden;
                }
            }
        }
    }

    #right {
        justify-content: space-evenly;
    }
</style>