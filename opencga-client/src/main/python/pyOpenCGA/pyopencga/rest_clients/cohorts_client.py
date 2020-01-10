from pyopencga.rest_clients._parent_rest_clients import _ParentRestClient


class Cohorts(_ParentRestClient):
    """
    This class contains methods for the 'Cohorts' webservices
    Client version: 2.0.0
    PATH: /{apiVersion}/cohorts
    """

    def __init__(self, configuration, token=None, login_handler=None, *args, **kwargs):
        _category = 'cohorts'
        super(Cohorts, self).__init__(configuration, _category, token, login_handler, *args, **kwargs)

    def delete(self, cohorts, **options):
        """
        Delete cohorts
        PATH: /{apiVersion}/cohorts/{cohorts}/delete

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param str cohorts: Comma separated list of cohort ids
        """

        return self._delete('delete', query_id=cohorts, **options)

    def create(self, data, **options):
        """
        Create a cohort
        PATH: /{apiVersion}/cohorts/create

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param str variable_set: Variable set id or name
        :param str variable: Variable name
        :param dict data: JSON containing cohort information
        """

        return self._post('create', data=data, **options)

    def update(self, cohorts, data=None, **options):
        """
        Update some cohort attributes
        PATH: /{apiVersion}/cohorts/{cohorts}/update

        :param str cohorts: Comma separated list of cohort ids
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param str annotation_sets_action: Action to be performed if the array of annotationSets is being updated.
        :param dict data: params
        """

        return self._post('update', query_id=cohorts, data=data, **options)

    def aggregation_stats(self, **options):
        """
        Fetch catalog cohort stats
        PATH: /{apiVersion}/cohorts/aggregationStats

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param str type: Type
        :param str creation_year: Creation year
        :param str creation_month: Creation month (JANUARY, FEBRUARY...)
        :param str creation_day: Creation day
        :param str creation_day_of_week: Creation day of week (MONDAY, TUESDAY...)
        :param str num_samples: Number of samples
        :param str status: Status
        :param str release: Release
        :param str annotation: Annotation, e.g: key1=value(,key2=value)
        :param bool default: Calculate default stats
        :param str field: List of fields separated by semicolons, e.g.: studies;type. For nested fields use >>, e.g.: studies>>biotype;type;numSamples[0..10]:1
        """

        return self._get('aggregationStats', **options)

    def info(self, cohorts, **options):
        """
        Get cohort information
        PATH: /{apiVersion}/cohorts/{cohorts}/info

        :param str include: Fields included in the response, whole JSON path must be provided
        :param str exclude: Fields excluded in the response, whole JSON path must be provided
        :param bool flatten_annotations: Flatten the annotations?
        :param str cohorts: Comma separated list of cohort names or ids up to a maximum of 100
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param bool deleted: Boolean to retrieve deleted cohorts
        """

        return self._get('info', query_id=cohorts, **options)

    def update_annotations(self, cohort, annotation_set, data=None, **options):
        """
        Update annotations from an annotationSet
        PATH: /{apiVersion}/cohorts/{cohort}/annotationSets/{annotationSet}/annotations/update

        :param str cohort: Cohort id
        :param str study: study
        :param str annotation_set: AnnotationSet id to be updated.
        :param str action: Action to be performed: ADD to add new annotations; REPLACE to replace the value of an already existing annotation; SET to set the new list of annotations removing any possible old annotations; REMOVE to remove some annotations; RESET to set some annotations to the default value configured in the corresponding variables of the VariableSet if any.
        :param dict data: Json containing the map of annotations when the action is ADD, SET or REPLACE, a json with only the key 'remove' containing the comma separated variables to be removed as a value when the action is REMOVE or a json with only the key 'reset' containing the comma separated variables that will be set to the default value when the action is RESET
        """

        return self._post('annotationSets', query_id=cohort, subcategory='annotations/update', second_query_id=annotation_set, data=data, **options)

    def acl(self, cohorts, **options):
        """
        Return the acl of the cohort. If member is provided, it will only return the acl for the member.
        PATH: /{apiVersion}/cohorts/{cohorts}/acl

        :param str cohorts: Comma separated list of cohort names or ids up to a maximum of 100
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param str member: User or group id
        :param bool silent: Boolean to retrieve all possible entries that are queried for, false to raise an exception whenever one of the entries looked for cannot be shown for whichever reason
        """

        return self._get('acl', query_id=cohorts, **options)

    def update_acl(self, members, data, **options):
        """
        Update the set of permissions granted for the member
        PATH: /{apiVersion}/cohorts/acl/{members}/update

        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param str members: Comma separated list of user or group ids
        :param dict data: JSON containing the parameters to add ACLs
        """

        return self._post('update', query_id=members, data=data, **options)

    def search(self, **options):
        """
        Search cohorts
        PATH: /{apiVersion}/cohorts/search

        :param str include: Fields included in the response, whole JSON path must be provided
        :param str exclude: Fields excluded in the response, whole JSON path must be provided
        :param int limit: Number of results to be returned
        :param int skip: Number of results to skip
        :param bool count: Get the total number of results matching the query. Deactivated by default.
        :param bool flatten_annotations: Flatten the annotations?
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        :param str name: DEPRECATED: Name of the cohort
        :param str type: Cohort type
        :param str creation_date: Creation date. Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805
        :param str modification_date: Modification date. Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805
        :param bool deleted: Boolean to retrieve deleted cohorts
        :param str status: Status
        :param str annotation: Annotation, e.g: key1=value(,key2=value)
        :param str samples: Sample list
        :param str release: Release value
        """

        return self._get('search', **options)

    def samples(self, cohort, **options):
        """
        Get samples from cohort [DEPRECATED]
        PATH: /{apiVersion}/cohorts/{cohort}/samples

        :param str include: Fields included in the response, whole JSON path must be provided
        :param str exclude: Fields excluded in the response, whole JSON path must be provided
        :param int limit: Number of results to be returned
        :param int skip: Number of results to skip
        :param bool count: Get the total number of results matching the query. Deactivated by default.
        :param str cohort: Cohort id or name
        :param str study: Study [[user@]project:]study where study and project can be either the ID or UUID
        """

        return self._get('samples', query_id=cohort, **options)
